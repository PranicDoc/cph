package com.pranicdoc.cph.review;

import com.pranicdoc.cph.common.security.InternalApiKeyFilter;
import com.pranicdoc.cph.review.controller.InternalPdfJobController;
import com.pranicdoc.cph.review.event.PdfReadyEvent;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.service.PdfJobCaseResolver;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller + the key filter in front of it (the filter itself is covered by InternalApiKeyFilterTest). */
class InternalPdfJobControllerTest {

    private static final String PATH = "/api/v1/internal/pdf-jobs/job-1/completed";
    private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final UUID HEALER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private final ReviewAutoSubmitService autoSubmit = mock(ReviewAutoSubmitService.class);
    private final PdfJobCaseResolver jobCases = mock(PdfJobCaseResolver.class);
    private final ReviewEventPublisher events = mock(ReviewEventPublisher.class);

    private MockMvc mvcWithKey(String configuredKey) {
        return MockMvcBuilders
                .standaloneSetup(new InternalPdfJobController(autoSubmit, jobCases, events))
                .addFilters(new InternalApiKeyFilter(configuredKey))
                .build();
    }

    private MockMvc mvc() {
        return mvcWithKey("s3cret");
    }

    @Test
    void missingKey_401() throws Exception {
        mvc().perform(post(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(autoSubmit, jobCases, events);
    }

    @Test
    void wrongKey_401() throws Exception {
        mvc().perform(post(PATH).header(InternalApiKeyFilter.HEADER, "nope")).andExpect(status().isUnauthorized());
        verifyNoInteractions(autoSubmit, jobCases, events);
    }

    @Test
    void unconfiguredKey_404_evenWithAnEmptyHeader() throws Exception {
        mvcWithKey("").perform(post(PATH).header(InternalApiKeyFilter.HEADER, "")).andExpect(status().isNotFound());
        mvcWithKey("").perform(post(PATH)).andExpect(status().isNotFound());
        verifyNoInteractions(autoSubmit, jobCases, events);
    }

    @Test
    void validKey_204_publishesPdfReady_andRunsAutoSubmit() throws Exception {
        when(jobCases.resolve("job-1")).thenReturn(Optional.of(new PdfJobCaseResolver.JobCase(CASE_ID, HEALER_ID)));

        mvc().perform(post(PATH).header(InternalApiKeyFilter.HEADER, "s3cret")).andExpect(status().isNoContent());

        verify(events).publishPdfReady(new PdfReadyEvent(HEALER_ID, CASE_ID, "job-1"));
        verify(autoSubmit).onPdfCompleted("job-1");
    }

    @Test
    void unknownJob_204_publishesNothing() throws Exception {
        when(jobCases.resolve("ghost")).thenReturn(Optional.empty());

        mvc().perform(post("/api/v1/internal/pdf-jobs/ghost/completed").header(InternalApiKeyFilter.HEADER, "s3cret"))
                .andExpect(status().isNoContent());

        verify(events, never()).publishPdfReady(any());
        verify(autoSubmit).onPdfCompleted("ghost");
    }

    @Test
    void publisherFails_still204_andAutoSubmitStillRuns() throws Exception {
        when(jobCases.resolve("job-1")).thenReturn(Optional.of(new PdfJobCaseResolver.JobCase(CASE_ID, HEALER_ID)));
        doThrow(new IllegalStateException("redis down")).when(events).publishPdfReady(any());

        mvc().perform(post(PATH).header(InternalApiKeyFilter.HEADER, "s3cret")).andExpect(status().isNoContent());

        verify(autoSubmit).onPdfCompleted("job-1");
    }

    @Test
    void autoSubmitThrows_still204() throws Exception {
        doThrow(new IllegalStateException("boom")).when(autoSubmit).onPdfCompleted("job-1");

        mvc().perform(post(PATH).header(InternalApiKeyFilter.HEADER, "s3cret")).andExpect(status().isNoContent());
    }
}
