package com.pranicdoc.cph.masterdata.model;

/**
 * ColumnDataType — the fixed set of data types a masterdata column-config
 * row may declare (MDIMPORT-04). Plain fixed-set enum, no {@code @Enumerated}
 * mapping annotation here — that annotation lives on the consuming entity
 * field ({@link MasterdataColumnConfig#getDataType()}), matching how
 * {@code Operation}/{@code Resource} are consumed elsewhere in this codebase.
 */
public enum ColumnDataType {
    STRING,
    NUMBER,
    LIST,
    ENUM
}
