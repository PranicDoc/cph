package com.pranicdoc.cph.healing.repository;

import com.pranicdoc.cph.healing.model.PdfJobEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PdfJobRepository extends JpaRepository<PdfJobEntity, UUID> {
}
