package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssayEventRepository extends JpaRepository<AssayEvent, Long> {

    Optional<AssayEvent> findByEventNo(String eventNo);

    Optional<AssayEvent> findBySampleIdAndItemCode(Long sampleId, String itemCode);

    List<AssayEvent> findBySampleIdInOrderByEventTimeAsc(List<Long> sampleIds);
}
