package com.devsecops.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanFindingRepository extends JpaRepository<ScanFinding, Integer> {

    List<ScanFinding> findAllByOrderByDetectedAtDesc();

    List<ScanFinding> findAllByTargetUrlOrderByDetectedAtDesc(String targetUrl);
}
