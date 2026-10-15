#include "core/engine.h"

#include <chrono>
#include <cstring>

#include "perception/sight.h"

namespace astratto {

namespace {

unsigned extraThreads(unsigned asked) {
    if (asked == 0) {
        const unsigned hw = std::thread::hardware_concurrency();
        asked = hw > 1 ? hw - 1 : 1;
        if (asked > 8) asked = 8;          // route searches are short: past this the threads only wake each other
    }
    return asked > 0 ? asked - 1 : 0;       // the caller's thread is one of the workers
}

/** Reads a record of a known kind out of a stream that need not be aligned for it. */
template <typename T> bool read(const uint8_t* at, uint32_t bytes, T& out) {
    if (bytes < sizeof(T)) return false;
    std::memcpy(&out, at, sizeof(T));
    return true;
}

}  // namespace

Engine::Engine(unsigned workerThreads) : pool_(extraThreads(workerThreads)) {
    for (unsigned i = 0; i < pool_.workers(); i++) finders_.emplace_back(new Pathfinder());
    stats_.struct_size = sizeof(astratto_stats);
    stats_.workers = pool_.workers();
}

template <typename T> void Engine::emit(const T& head, const void* tail, size_t tailBytes) {
    const size_t at = events_.size();
    events_.resize(at + sizeof(T) + tailBytes);
    std::memcpy(events_.data() + at, &head, sizeof(T));
    if (tailBytes) std::memcpy(events_.data() + at + sizeof(T), tail, tailBytes);
    eventCount_++;
}

int32_t Engine::frame(const uint8_t* commands, uint32_t bytes, float /*dt*/) {
    const auto t0 = std::chrono::steady_clock::now();
    events_.clear();
    eventCount_ = 0;
    paths_.clear();
    sights_.clear();
    int32_t status = ASTRATTO_OK;

    // 1. the batch, in the order it was written: changes to the world are made now, questions are kept for after
    uint32_t at = 0;
    while (at < bytes) {
        astratto_record head;
        if (bytes - at < sizeof(head)) { fail("command stream ends inside a record header"); status = ASTRATTO_ERR_STREAM; break; }
        std::memcpy(&head, commands + at, sizeof(head));
        if (head.bytes < sizeof(head) || (head.bytes & 3u) != 0 || head.bytes > bytes - at) { fail("command record with a bad size"); status = ASTRATTO_ERR_STREAM; break; }
        const uint8_t* rec = commands + at;
        bool ok = true;
        switch (head.type) {
            case ASTRATTO_CMD_BLOCK_SET: {
                astratto_cmd_block_set c;
                if ((ok = read(rec, head.bytes, c))) { world_.set(c.x, c.y, c.z, static_cast<int>(c.block)); stats_.block_edits++; }
                break;
            }
            case ASTRATTO_CMD_DOOR_SEAL: {
                astratto_cmd_door_seal c;
                if ((ok = read(rec, head.bytes, c))) world_.seal(c.x, c.y, c.z, c.sealed != 0);
                break;
            }
            case ASTRATTO_CMD_DOORS_UNSEAL: world_.unsealAll(); break;
            case ASTRATTO_CMD_NAV_TUNING: {
                astratto_cmd_nav_tuning c;
                if ((ok = read(rec, head.bytes, c))) { tuning_.jumpCost = c.jump_cost; tuning_.ladderCost = c.ladder_cost; tuning_.mantleCost = c.mantle_cost; }
                break;
            }
            case ASTRATTO_CMD_PATH_REQUEST: {
                Path p;
                if ((ok = read(rec, head.bytes, p.cmd))) paths_.push_back(std::move(p));
                break;
            }
            case ASTRATTO_CMD_SIGHT_REQUEST: {
                Sight s;
                if ((ok = read(rec, head.bytes, s.cmd))) sights_.push_back(s);
                break;
            }
            default: break;   // a command from a newer version of the interface: skipped, by its own stated size
        }
        if (!ok) { fail("command record shorter than its kind"); status = ASTRATTO_ERR_STREAM; break; }
        stats_.commands++;
        at += head.bytes;
    }

    // 2. the questions, against the world as the batch left it. Nothing changes the world while they run, so they
    //    are shared out among the workers; each answer depends only on its own question.
    if (!paths_.empty() || !sights_.empty()) {
        if (!world_.loaded()) {
            fail("a path or sight request before any world was loaded");
            if (status == ASTRATTO_OK) status = ASTRATTO_ERR_STATE;
        } else {
            pool_.parallelFor(paths_.size(), [this](size_t i, unsigned worker) {
                Path& p = paths_[i];
                PathQuery q;
                q.sx = p.cmd.sx; q.sy = p.cmd.sy; q.sz = p.cmd.sz; q.tx = p.cmd.tx; q.ty = p.cmd.ty; q.tz = p.cmd.tz;
                q.maxExpand = static_cast<int>(p.cmd.max_expand);
                q.doors = (p.cmd.flags & ASTRATTO_PATH_DOORS) != 0;
                q.goalY = (p.cmd.flags & ASTRATTO_PATH_GOAL_Y) != 0;
                q.mantle = (p.cmd.flags & ASTRATTO_PATH_MANTLE) != 0;
                p.found = finders_[worker]->path(world_, tuning_, q, p.cells, p.expansions);
            });
            pool_.parallelFor(sights_.size(), [this](size_t i, unsigned) {
                Sight& s = sights_[i];
                s.clear = sightClear(world_, s.cmd.ax, s.cmd.ay, s.cmd.az, s.cmd.bx, s.cmd.by, s.cmd.bz, s.cmd.mode == ASTRATTO_SIGHT_EYE);
            });
        }
    }

    // 3. the answers, in the order the questions were asked
    if (world_.loaded()) {
        for (const Path& p : paths_) {
            astratto_evt_path_result e;
            const size_t tail = p.cells.size() * sizeof(int32_t);
            e.head.type = ASTRATTO_EVT_PATH_RESULT;
            e.head.bytes = static_cast<uint32_t>(sizeof(e) + tail);
            e.request = p.cmd.request;
            e.status = p.found ? ASTRATTO_PATH_FOUND : ASTRATTO_PATH_NONE;
            e.expansions = p.expansions;
            e.cell_count = static_cast<uint32_t>(p.cells.size() / 3);
            emit(e, p.cells.data(), tail);
            stats_.path_requests++;
            stats_.path_expansions += p.expansions;
            if (p.found) stats_.paths_found++;
        }
        for (const Sight& s : sights_) {
            astratto_evt_sight_result e;
            e.head.type = ASTRATTO_EVT_SIGHT_RESULT;
            e.head.bytes = sizeof(e);
            e.request = s.cmd.request;
            e.clear = s.clear ? 1u : 0u;
            emit(e, nullptr, 0);
            stats_.sight_requests++;
        }
    }

    stats_.frames++;
    const float ms = std::chrono::duration<float, std::milli>(std::chrono::steady_clock::now() - t0).count();
    stats_.last_frame_ms = ms;
    if (ms > stats_.worst_frame_ms) stats_.worst_frame_ms = ms;
    return status;
}

const astratto_stats& Engine::stats() {
    stats_.nav_version = world_.navVersion();
    stats_.sealed_doors = world_.sealedCount();
    stats_.world_x = static_cast<uint32_t>(world_.sizeX());
    stats_.world_y = static_cast<uint32_t>(world_.sizeY());
    stats_.world_z = static_cast<uint32_t>(world_.sizeZ());
    return stats_;
}

}  // namespace astratto
