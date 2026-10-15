#include "world/voxel_world.h"

#include <cstring>

namespace astratto {

VoxelWorld::VoxelWorld() { std::memset(defs_, 0, sizeof(defs_)); }

void VoxelWorld::setTable(const astratto_block_def* defs, uint32_t count) {
    std::memset(defs_, 0, sizeof(defs_));
    if (defs == nullptr) return;
    if (count > 256) count = 256;
    std::memcpy(defs_, defs, sizeof(astratto_block_def) * count);
    navVersion_++;
}

bool VoxelWorld::load(uint32_t sx, uint32_t sy, uint32_t sz, uint32_t belowFloor, const uint8_t* cells, uint64_t bytes) {
    // the path search packs a cell into 32 bits: ten for x, ten for z, the rest for y
    if (sx == 0 || sy == 0 || sz == 0 || sx > 1024 || sz > 1024 || sy > 2048 || belowFloor > 255) return false;
    const uint64_t need = static_cast<uint64_t>(sx) * sy * sz;
    if (cells == nullptr || bytes != need) return false;
    blocks_.assign(cells, cells + need);
    sx_ = static_cast<int>(sx); sy_ = static_cast<int>(sy); sz_ = static_cast<int>(sz);
    belowFloor_ = static_cast<int>(belowFloor);
    sealed_.clear();
    navVersion_++;
    return true;
}

bool VoxelWorld::set(int x, int y, int z, int id) {
    if (!inBounds(x, y, z)) return false;
    const size_t i = index(x, y, z);
    const int old = blocks_[i];
    id &= 0xFF;
    if (old == id) return false;
    blocks_[i] = static_cast<uint8_t>(id);
    const uint32_t a = defs_[old].flags, b = defs_[id].flags;
    const bool nav = ((a ^ b) & (ASTRATTO_BLOCK_SOLID | ASTRATTO_BLOCK_DOOR)) != 0;
    if (nav) navVersion_++;
    return nav;
}

void VoxelWorld::seal(int x, int y, int z, bool sealed) {
    const uint64_t k = key(x, y, z);
    const bool changed = sealed ? sealed_.insert(k).second : sealed_.erase(k) != 0;
    if (changed) navVersion_++;
}

}  // namespace astratto
