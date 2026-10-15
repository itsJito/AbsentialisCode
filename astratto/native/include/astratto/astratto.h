/*
 * Astratto Engine - the native core's public interface.
 *
 * This header is the whole of the bridge between the engine (C++) and whatever is built on it (Absentialis' gameplay
 * is Java; a future game need not be). It is plain C, so that any language with a foreign-function interface can bind
 * it, and it is versioned, so that a game built against one engine refuses to run on another instead of misreading it.
 *
 * The rules of the boundary - they are what this file is shaped by:
 *
 *   1. The engine owns native memory and the authoritative state of what it simulates. The caller never frees or
 *      writes anything the engine hands out; pointers returned by the engine are read-only and stay valid until the
 *      next call of the function that returned them.
 *   2. Nothing is asked of the engine one object at a time. The caller writes a batch of COMMANDS into a buffer and
 *      hands the whole batch over once a tick (astratto_frame). The engine answers with a batch of EVENTS.
 *   3. State is read back as SNAPSHOTS: immutable blocks of plain data (astratto_snapshot), never live objects.
 *   4. The engine never calls back into the caller. Not from the frame, not from a worker thread, not ever.
 *   5. Every structure starts with its own size, and every function takes pointers only and returns a status. That
 *      is what lets a structure grow in a later minor version without breaking a caller built against an older one.
 *
 * Byte order is the machine's (little-endian on every platform the engine builds for). All records in command and
 * event streams are aligned to four bytes.
 *
 * Copyright (c) Astratto North of LudusValley Recreations, under VEIN Enterprises.
 */
#ifndef ASTRATTO_H
#define ASTRATTO_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
#  if defined(ASTRATTO_BUILD)
#    define ASTRATTO_API __declspec(dllexport)
#  elif defined(ASTRATTO_STATIC)
#    define ASTRATTO_API
#  else
#    define ASTRATTO_API __declspec(dllimport)
#  endif
#else
#  define ASTRATTO_API __attribute__((visibility("default")))
#endif

/* ------------------------------------------------------------------ version */
/* MAJOR moves when a caller built against the old header would misbehave; MINOR when something was only added. */
#define ASTRATTO_API_MAJOR 1
#define ASTRATTO_API_MINOR 1
#define ASTRATTO_API_VERSION ((uint32_t)((ASTRATTO_API_MAJOR << 16) | ASTRATTO_API_MINOR))

/* ------------------------------------------------------------------ status */
enum {
    ASTRATTO_OK = 0,
    ASTRATTO_ERR_ARGUMENT = -1,   /* a null pointer, a structure too small, a value out of range */
    ASTRATTO_ERR_VERSION = -2,    /* the caller was built against an incompatible major version */
    ASTRATTO_ERR_STATE = -3,      /* asked for before what it needs exists (a path before a world) */
    ASTRATTO_ERR_MEMORY = -4,
    ASTRATTO_ERR_STREAM = -5,     /* a malformed command stream; nothing after the bad record was run */
    ASTRATTO_ERR_INTERNAL = -6
};

typedef struct astratto_engine astratto_engine;

/* ------------------------------------------------------------------ making and ending an engine */
typedef struct astratto_config {
    uint32_t struct_size;
    uint32_t api_version;      /* ASTRATTO_API_VERSION the caller was compiled against */
    uint32_t worker_threads;   /* 0 = one per hardware thread, less one (the caller's own thread works too) */
    uint32_t reserved;
} astratto_config;

ASTRATTO_API uint32_t astratto_api_version(void);
ASTRATTO_API const char* astratto_build_info(void);
/* Returns null on failure; astratto_create_status says why. */
ASTRATTO_API astratto_engine* astratto_create(const astratto_config* config);
ASTRATTO_API int32_t astratto_create_status(void);
ASTRATTO_API void astratto_destroy(astratto_engine* engine);
/* The text of the last error on this engine (never null; empty when there has been none). */
ASTRATTO_API const char* astratto_last_error(astratto_engine* engine);

/* ------------------------------------------------------------------ the world: voxels */
/*
 * Block kinds are the game's, not the engine's: the engine is told only what it needs to know about each of the 256
 * possible ids. Flags the engine understands:
 */
enum {
    ASTRATTO_BLOCK_SOLID = 1u << 0,    /* a body cannot be where it is */
    ASTRATTO_BLOCK_OPAQUE = 1u << 1,   /* nothing is seen through it */
    ASTRATTO_BLOCK_CUTOUT = 1u << 2,   /* mesh or grating: solid, but seen through */
    ASTRATTO_BLOCK_CLIMB = 1u << 3,    /* something to hold (a ladder) */
    ASTRATTO_BLOCK_STEP = 1u << 4,     /* a stair tread: a step up onto it is walked, not jumped */
    ASTRATTO_BLOCK_LIQUID = 1u << 5,   /* waded through, slowly */
    ASTRATTO_BLOCK_HAZARD = 1u << 6,   /* never routed over or through */
    ASTRATTO_BLOCK_DOOR = 1u << 7      /* a door leaf: passable to those who open doors unless it is sealed */
};
enum { ASTRATTO_MATERIAL_OTHER = 0, ASTRATTO_MATERIAL_GLASS = 1, ASTRATTO_MATERIAL_FOLIAGE = 2 };

typedef struct astratto_block_def {
    uint32_t flags;
    uint16_t hide_mask;   /* for shaped blocks: bit n set = the shape hides a line crossing sixteenth n of its height */
    uint8_t material;     /* ASTRATTO_MATERIAL_* */
    uint8_t shape;        /* 0 = a full cube; anything else = furniture or a fitting (see hide_mask) */
} astratto_block_def;

typedef struct astratto_block_table {
    uint32_t struct_size;
    uint32_t count;                    /* at most 256; ids not given are air */
    const astratto_block_def* defs;
} astratto_block_table;

/*
 * A whole world at once. Cells are one byte each, laid out column by column: index = (x * size_z + z) * size_y + y.
 * The engine copies them; from then on its copy is changed only by ASTRATTO_CMD_BLOCK_SET.
 * Limits: size_x and size_z at most 1024, size_y at most 2048.
 */
typedef struct astratto_world_desc {
    uint32_t struct_size;
    uint32_t size_x, size_y, size_z;
    uint32_t below_floor_block;        /* the id reported for any cell under y = 0 */
    uint32_t reserved;
    const uint8_t* blocks;             /* size_x * size_y * size_z bytes */
    uint64_t block_bytes;
} astratto_world_desc;

ASTRATTO_API int32_t astratto_world_block_table(astratto_engine* engine, const astratto_block_table* table);
ASTRATTO_API int32_t astratto_world_load(astratto_engine* engine, const astratto_world_desc* world);

/* ------------------------------------------------------------------ commands (caller -> engine) */
/*
 * A command stream is a run of records. Each begins with this header; 'bytes' is the size of the whole record,
 * header included, and is a multiple of four. Unknown commands from a newer minor version are skipped.
 */
typedef struct astratto_record {
    uint32_t type;
    uint32_t bytes;
} astratto_record;

enum {
    ASTRATTO_CMD_BLOCK_SET = 1,       /* astratto_cmd_block_set */
    ASTRATTO_CMD_DOOR_SEAL = 2,       /* astratto_cmd_door_seal */
    ASTRATTO_CMD_NAV_TUNING = 3,      /* astratto_cmd_nav_tuning */
    ASTRATTO_CMD_DOORS_UNSEAL = 4,    /* a bare astratto_record: every door is unsealed (a new set of doors follows) */
    ASTRATTO_CMD_PATH_REQUEST = 16,   /* astratto_cmd_path_request -> ASTRATTO_EVT_PATH_RESULT */
    ASTRATTO_CMD_SIGHT_REQUEST = 17   /* astratto_cmd_sight_request -> ASTRATTO_EVT_SIGHT_RESULT */
};

typedef struct astratto_cmd_block_set { astratto_record head; int32_t x, y, z; uint32_t block; } astratto_cmd_block_set;
/* A door cell that may not simply be opened (locked and still latched), or may again. */
typedef struct astratto_cmd_door_seal { astratto_record head; int32_t x, y, z; uint32_t sealed; } astratto_cmd_door_seal;
typedef struct astratto_cmd_nav_tuning { astratto_record head; float jump_cost, ladder_cost, mantle_cost; uint32_t reserved; } astratto_cmd_nav_tuning;

enum {
    ASTRATTO_PATH_DOORS = 1u << 0,     /* the walker opens doors (and pulls itself up ledges, with MANTLE) */
    ASTRATTO_PATH_GOAL_Y = 1u << 1,    /* the goal is on a particular floor (ty is meaningful) */
    ASTRATTO_PATH_MANTLE = 1u << 2
};
typedef struct astratto_cmd_path_request {
    astratto_record head;
    uint32_t request;                  /* the caller's own number for it; comes back with the result */
    uint32_t flags;
    float sx, sy, sz;
    float tx, ty, tz;
    uint32_t max_expand;
    uint32_t reserved;
} astratto_cmd_path_request;

enum {
    ASTRATTO_SIGHT_LIGHT = 0,          /* stops at opaque blocks only */
    ASTRATTO_SIGHT_EYE = 1             /* what an eye sees through: walls, shut doors, furniture and canopy stop it */
};
typedef struct astratto_cmd_sight_request {
    astratto_record head;
    uint32_t request;
    uint32_t mode;
    float ax, ay, az;
    float bx, by, bz;
} astratto_cmd_sight_request;

/* ------------------------------------------------------------------ events (engine -> caller) */
enum {
    ASTRATTO_EVT_PATH_RESULT = 16,    /* astratto_evt_path_result, then cell_count * 3 int32 (x, y, z) */
    ASTRATTO_EVT_SIGHT_RESULT = 17    /* astratto_evt_sight_result */
};
enum { ASTRATTO_PATH_FOUND = 0, ASTRATTO_PATH_NONE = 1 };

typedef struct astratto_evt_path_result {
    astratto_record head;
    uint32_t request;
    uint32_t status;                   /* ASTRATTO_PATH_FOUND (cells follow; may stop short of the goal) or _NONE */
    uint32_t expansions;
    uint32_t cell_count;
} astratto_evt_path_result;

typedef struct astratto_evt_sight_result {
    astratto_record head;
    uint32_t request;
    uint32_t clear;                    /* 1 = nothing in the way */
} astratto_evt_sight_result;

/* ------------------------------------------------------------------ one tick */
/*
 * Hands over a batch of commands and runs the engine for dt seconds. Commands that change the world are applied in
 * the order written; requests are then answered against the world as the batch left it. On return 'events' points at
 * the engine's own memory - the answers, in the order the requests were written - and stays valid until the next
 * call of astratto_frame on this engine.
 */
typedef struct astratto_frame_io {
    uint32_t struct_size;
    float dt;
    const uint8_t* commands;
    uint32_t command_bytes;
    uint32_t reserved;
    const uint8_t* events;             /* out */
    uint32_t event_bytes;              /* out */
    uint32_t event_count;              /* out */
} astratto_frame_io;

ASTRATTO_API int32_t astratto_frame(astratto_engine* engine, astratto_frame_io* io);

/* ------------------------------------------------------------------ snapshots */
enum { ASTRATTO_SNAPSHOT_STATS = 1 };

typedef struct astratto_stats {
    uint32_t struct_size;
    uint32_t workers;
    uint64_t frames;
    uint64_t commands;
    uint64_t block_edits;
    uint64_t path_requests;
    uint64_t path_expansions;
    uint64_t paths_found;
    uint64_t sight_requests;
    uint32_t nav_version;              /* moves whenever a change could open or close a way through */
    uint32_t sealed_doors;
    float last_frame_ms;
    float worst_frame_ms;
    uint32_t world_x, world_y, world_z;
    uint32_t reserved;
} astratto_stats;

typedef struct astratto_snapshot_io {
    uint32_t struct_size;
    uint32_t kind;                     /* ASTRATTO_SNAPSHOT_* */
    const void* data;                  /* out: the engine's memory, valid until the next astratto_snapshot */
    uint64_t bytes;                    /* out */
} astratto_snapshot_io;

ASTRATTO_API int32_t astratto_snapshot(astratto_engine* engine, astratto_snapshot_io* io);

/* ------------------------------------------------------------------ rendering: voxel meshing (1.1) */
/*
 * The renderer's chunk mesher. Unlike the simulation it does not run inside astratto_frame: the renderer meshes on
 * threads of its own, at load for the whole world and afterwards for whatever columns changed, and needs no tick. A
 * MESHER is made from a render table and used by one thread at a time (make one per meshing thread); it holds nothing
 * of the world. Each call hands it one column - sixteen cells square, with a border of one cell on every side, the
 * world's whole height - and its light, and gets back the column's vertex streams. The unit of work is a column of
 * some twenty-five thousand cells, which is the batch.
 *
 * Output vertices are 12 bytes (see render/chunk_mesher.h); four make a quad, the renderer draws quads.
 */
typedef struct astratto_mesher astratto_mesher;

enum {
    ASTRATTO_RB_CROSS = 1u << 0,        /* drawn as two crossed planes (grass, flames, webs) */
    ASTRATTO_RB_TRANSLUCENT = 1u << 1,  /* into the translucent stream (glass, water) */
    ASTRATTO_RB_LIQUID = 1u << 2,
    ASTRATTO_RB_CUTOUT = 1u << 3,       /* alpha-tested (leaves, mesh) */
    ASTRATTO_RB_EMISSIVE = 1u << 4,     /* its texture glows */
    ASTRATTO_RB_LEAF = 1u << 5,         /* foliage: leaves shade leaves, softly */
    ASTRATTO_RB_SWAY = 1u << 6,         /* moves in the wind (leaves, laundry) */
    ASTRATTO_RB_OCCLUDES = 1u << 7,     /* an opaque full cube: hides the faces against it and darkens corners */
    ASTRATTO_RB_DOOR = 1u << 8,         /* a door: left out unless asked for (in play its leaf is an entity) */
    ASTRATTO_RB_SOLID = 1u << 9,
    ASTRATTO_RB_STEP = 1u << 10,        /* a stair tread */
    ASTRATTO_RB_SEAT = 1u << 11,        /* a table or a desk: a chair beside it faces it */
    ASTRATTO_RB_PLANT = 1u << 12        /* a crossed plant the renderer may grow as foliage instead (ASTRATTO_MESH_NO_PLANTS) */
};
typedef struct astratto_render_block {
    uint16_t layer_top, layer_side, layer_bottom;   /* texture array layers (0..1023) */
    uint16_t flags;                                  /* ASTRATTO_RB_* */
    uint8_t var_top, var_side, var_bottom;           /* texture variants (1..4) */
    uint8_t shape;                                   /* 0 = a full cube, else an index into the shape table */
} astratto_render_block;

/* How a shaped block reads the way it faces from what is round it (no block stores a direction). */
enum {
    ASTRATTO_ORIENT_NONE = 0,
    ASTRATTO_ORIENT_BACKING = 1,   /* back to the first full cube of -z, +x, +z, -x; else scattered */
    ASTRATTO_ORIENT_DOOR = 2,      /* in the line of its wall */
    ASTRATTO_ORIENT_JOIN = 3,      /* a fence: along its run; both ways at a corner (two passes) */
    ASTRATTO_ORIENT_CHAIR = 4,     /* facing a table beside it; else scattered */
    ASTRATTO_ORIENT_PEW = 5,       /* across a row of its own kind */
    ASTRATTO_ORIENT_SCATTER = 6,   /* any way, the same for the same cell */
    ASTRATTO_ORIENT_STAIR = 7      /* rising toward the next tread of its flight */
};
typedef struct astratto_shape_def {
    uint8_t orient;                /* ASTRATTO_ORIENT_* */
    uint8_t joins;                 /* a fence joins onto it (posts, fittings, arms, fences) */
    uint8_t invisible;             /* geometry only for bodies and eyes, never drawn */
    uint8_t present;               /* 0 = no boxes at all */
    uint32_t first_box, box_count; /* into the box array: 8 bytes a box, x0 y0 z0 x1 y1 z1 (sixteenths) tex pad */
} astratto_shape_def;

typedef struct astratto_render_table {
    uint32_t struct_size;
    uint32_t block_count;                   /* at most 256 */
    const astratto_render_block* blocks;
    uint32_t shape_count;
    uint32_t box_count;
    const astratto_shape_def* shapes;
    const int8_t* boxes;
    uint32_t below_floor_block;             /* the id of every cell under y = 0 */
    uint32_t reserved;
} astratto_render_table;

enum { ASTRATTO_MESH_DOORS = 1u << 0, ASTRATTO_MESH_NO_PLANTS = 1u << 1 };
typedef struct astratto_mesh_io {
    uint32_t struct_size;
    uint32_t flags;                         /* ASTRATTO_MESH_* */
    uint32_t size_y;                        /* the world's height (at most 2048) */
    uint32_t reserved;
    int32_t origin_x, origin_z;             /* world position of the column's first cell */
    int32_t world_x, world_z;               /* the world's size: outside it is a hard edge */
    const uint8_t* blocks;                  /* 18 * size_y * 18 cells: index ((x + 1) * 18 + (z + 1)) * size_y + y, x and z -1..16 */
    const uint8_t* light;                   /* the same layout: sky light in the high four bits, block light in the low */
    const uint8_t* opaque;                  /* out */
    const uint8_t* translucent;             /* out */
    uint32_t opaque_bytes;                  /* out */
    uint32_t translucent_bytes;             /* out */
} astratto_mesh_io;

/* Returns null on a malformed table. */
ASTRATTO_API astratto_mesher* astratto_mesher_create(const astratto_render_table* table);
ASTRATTO_API void astratto_mesher_destroy(astratto_mesher* mesher);
/* The out pointers are the mesher's memory, valid until its next call. */
ASTRATTO_API int32_t astratto_mesh_column(astratto_mesher* mesher, astratto_mesh_io* io);

#ifdef __cplusplus
}
#endif
#endif
