package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.HandoverEvent;
import com.chris64233.cc.assaychain.domain.HandoverStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HandoverEventRepository extends JpaRepository<HandoverEvent, Long> {

    Optional<HandoverEvent> findByEventNo(String eventNo);

    boolean existsBySampleIdAndStatus(Long sampleId, HandoverStatus status);

    List<HandoverEvent> findBySampleIdInOrderByInitiatedAtAscIdAsc(List<Long> sampleIds);
}
