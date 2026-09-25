package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.CustodyEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {

    Optional<CustodyEvent> findByEventNo(String eventNo);

    List<CustodyEvent> findBySampleIdInOrderByInitiatedAtAsc(List<Long> sampleIds);
}
