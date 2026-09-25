package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.AssayResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssayResultRepository extends JpaRepository<AssayResult, Long> {

    Optional<AssayResult> findByEventNo(String eventNo);

    Optional<AssayResult> findBySampleIdAndTestItem(Long sampleId, String testItem);

    List<AssayResult> findBySampleIdInOrderByCreatedAtAscIdAsc(List<Long> sampleIds);
}
