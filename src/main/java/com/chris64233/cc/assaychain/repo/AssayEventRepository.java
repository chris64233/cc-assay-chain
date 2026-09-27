package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.AssayEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssayEventRepository extends JpaRepository<AssayEvent, Long> {

    Optional<AssayEvent> findByEventNo(String eventNo);

    /** 版本链：同一（样本，检测项目）的全部版本，按版本号升序。 */
    List<AssayEvent> findBySampleIdAndItemCodeOrderByVersionNoAsc(Long sampleId, String itemCode);

    Optional<AssayEvent> findBySampleIdAndItemCodeAndVersionNo(
            Long sampleId, String itemCode, int versionNo);

    List<AssayEvent> findBySampleIdInOrderByEventTimeAsc(List<Long> sampleIds);
}
