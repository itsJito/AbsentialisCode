// Astratto Engine - the voxel world: one byte a cell, and what each kind of block means to the engine.
#pragma once

#include <cstdint>
#include <unordered_set>
#include <vector>

#include "astratto/astratto.h"

namespace astratto {

class VoxelWorld {
public:
    VoxelWorld();

    void setTable(const astratto_block_def* defs, uint32_t count);
    /** Copies a whole world in. False when the sizes are out of range or do not match the bytes given. */
    bool load(uint32_t sx, uint32_t sy, uint32_t sz, uint32_t belowFloor, const uint8_t* cells, uint64_t bytes);
    bool loaded() const { return !blocks_.empty(); }

    int sizeX() const { return sx_; }
    int sizeY() const { return sy_; }
    int sizeZ() const { return sz_; }

    bool inBounds(int x, int y, int z) const { return x >= 0 && z >= 0 && y >= 0 && x < sx_ && z < sz_ && y < sy_; }

    /** The id at a cell: the floor block below the world, air beyond its sides and above it. */
    int get(int x, int y, int z) const {
        if (y < 0) return belowFloor_;
        if (!inBounds(x, y, z)) return 0;
        return blocks_[index(x, y, z)];
    }
    const astratto_block_def& def(int x, int y, int z) const { return defs_[get(x, y, z)]; }
    const astratto_block_def& defOf(int id) const { return defs_[id & 0xFF]; }

    /** The edge of the world is a wall; under it is ground; over it is sky. */
    bool solid(int x, int y, int z) const {
        if (y < 0) return true;
        if (x < 0 || z < 0 || x >= sx_ || z >= sz_) return true;
        if (y >= sy_) return false;
        return (defs_[blocks_[index(x, y, z)]].flags & ASTRATTO_BLOCK_SOLID) != 0;
    }

    /** Changes one cell. Returns true when the change could open or close a way through (the nav version moved). */
    bool set(int x, int y, int z, int id);

    void seal(int x, int y, int z, bool sealed);
    void unsealAll() { if (!sealed_.empty()) { sealed_.clear(); navVersion_++; } }
    bool sealed(int x, int y, int z) const { return !sealed_.empty() && sealed_.count(key(x, y, z)) != 0; }
    uint32_t sealedCount() const { return static_cast<uint32_t>(sealed_.size()); }

    uint32_t navVersion() const { return navVersion_; }

private:
    size_t index(int x, int y, int z) const { return (static_cast<size_t>(x) * sz_ + z) * sy_ + y; }
    static uint64_t key(int x, int y, int z) { return (static_cast<uint64_t>(static_cast<uint32_t>(x)) << 40) ^ (static_cast<uint64_t>(static_cast<uint32_t>(z)) << 16) ^ static_cast<uint32_t>(y); }

    int sx_ = 0, sy_ = 0, sz_ = 0, belowFloor_ = 0;
    std::vector<uint8_t> blocks_;
    astratto_block_def defs_[256];
    std::unordered_set<uint64_t> sealed_;
    uint32_t navVersion_ = 0;
};

}  // namespace astratto
