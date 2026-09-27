package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.AssayCorrection;
import com.chris64233.cc.assaychain.domain.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssayCorrectionRepository extends JpaRepository<AssayCorrection, Long> {

    Optional<AssayCorrection> findByCorrectionNo(String correctionNo);

    boolean existsByAssayEventIdAndStatus(Long assayEventId, CorrectionStatus status);

    List<AssayCorrection> findByAssayEventSampleIdInOrderByCreatedAtAsc(List<Long> sampleIds);
}
