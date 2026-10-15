// Astratto Engine - one engine: its world, its services, and the tick that runs a batch of commands through them.
#pragma once

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "astratto/astratto.h"
#include "core/job_pool.h"
#include "nav/pathfinder.h"
#include "world/voxel_world.h"

namespace astratto {

class Engine {
public:
    explicit Engine(unsigned workerThreads);

    VoxelWorld& world() { return world_; }

    /** Runs one batch. The events it produced are in events() until the next call. */
    int32_t frame(const uint8_t* commands, uint32_t bytes, float dt);
    const std::vector<uint8_t>& events() const { return events_; }
    uint32_t eventCount() const { return eventCount_; }

    const astratto_stats& stats();

    void fail(const std::string& what) { error_ = what; }
    const char* error() const { return error_.c_str(); }

private:
    struct Path { astratto_cmd_path_request cmd; std::vector<int32_t> cells; uint32_t expansions = 0; bool found = false; };
    struct Sight { astratto_cmd_sight_request cmd; bool clear = false; };

    template <typename T> void emit(const T& head, const void* tail, size_t tailBytes);

    VoxelWorld world_;
    NavTuning tuning_;
    JobPool pool_;
    std::vector<std::unique_ptr<Pathfinder>> finders_;   // one per worker
    std::vector<Path> paths_;
    std::vector<Sight> sights_;
    std::vector<uint8_t> events_;
    uint32_t eventCount_ = 0;
    astratto_stats stats_{};
    std::string error_;
};

}  // namespace astratto
