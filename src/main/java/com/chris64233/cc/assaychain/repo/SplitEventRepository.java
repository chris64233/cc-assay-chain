package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.SplitEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SplitEventRepository extends JpaRepository<SplitEvent, Long> {

    Optional<SplitEvent> findByEventNo(String eventNo);

    List<SplitEvent> findByParentSampleIdInOrderByCreatedAtAscIdAsc(List<Long> sampleIds);
}
