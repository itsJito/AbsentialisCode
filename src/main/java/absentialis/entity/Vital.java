package absentialis.entity;

/**
 * The explicit life states of a character (0.5). Transitions are made only by scene.Casualty:
 * ACTIVE -> DOWNED -> REVIVING -> ACTIVE, DOWNED -> DEAD, REVIVING -> DOWNED, ACTIVE -> DEAD.
 */
public enum Vital { ACTIVE, DOWNED, REVIVING, DEAD }
