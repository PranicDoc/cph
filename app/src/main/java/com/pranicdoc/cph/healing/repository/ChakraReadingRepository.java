package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.ChakraReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChakraReadingRepository extends JpaRepository<ChakraReading, UUID> {

    List<ChakraReading> findByChakraScanId(UUID scanId);
}
