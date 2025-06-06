package com.ohammer.apartner.domain.apartment.repository;

import com.ohammer.apartner.domain.apartment.entity.Unit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findByBuildingId(Long buildingId);
    Page<Unit> findByBuildingId(Long buildingId, Pageable pageable);
} 