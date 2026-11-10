package com.devsecops.dashboard;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanRecordRepository extends JpaRepository<ScanRecord, String> {

    List<ScanRecord> findAllByOrderByStartedAtDesc();

    List<ScanRecord> findAllByStatus(String status);
}
