package com.prosto.analytics.model;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "dataset_columns", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"dataset_id", "column_name"})
})
public class DatasetColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dataset_id", nullable = false)
    private Dataset dataset;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false)
    private FieldType fieldType;

    @Column(name = "category")
    private String category;

    @Column(nullable = false)
    private int ordinal;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Dataset getDataset() { return dataset; }
    public void setDataset(Dataset dataset) { this.dataset = dataset; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public FieldType getFieldType() { return fieldType; }
    public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
}
