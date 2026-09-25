package com.chris64233.cc.assaychain.repo;

import com.chris64233.cc.assaychain.domain.Sample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SampleRepository extends JpaRepository<Sample, Long> {

    Optional<Sample> findByExternalNo(String externalNo);

    List<Sample> findByParentId(Long parentId);

    List<Sample> findByParentIdIn(List<Long> parentIds);

    boolean existsByExternalNo(String externalNo);
}
