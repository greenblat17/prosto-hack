package com.prosto.analytics.repository;

import com.prosto.analytics.model.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
}
