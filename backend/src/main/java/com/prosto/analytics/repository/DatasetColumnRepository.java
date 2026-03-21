package com.prosto.analytics.repository;

import com.prosto.analytics.model.DatasetColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DatasetColumnRepository extends JpaRepository<DatasetColumn, UUID> {

    List<DatasetColumn> findByDatasetIdOrderByOrdinal(UUID datasetId);
}
