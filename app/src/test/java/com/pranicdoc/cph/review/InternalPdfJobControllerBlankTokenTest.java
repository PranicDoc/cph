package com.pranicdoc.cph.review;

import com.pranicdoc.cph.review.controller.InternalPdfJobController;
import com.pranicdoc.cph.review.event.ReviewEventPublisher;
import com.pranicdoc.cph.review.service.PdfJobCaseResolver;
import com.pranicdoc.cph.review.service.ReviewAutoSubmitService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** An unconfigured token must reject every call, even one that sends an empty header. */
class InternalPdfJobControllerBlankTokenTest {

    @Test
    void blankConfiguredToken_rejectsEverything() {
        ReviewAutoSubmitService autoSubmit = mock(ReviewAutoSubmitService.class);
        InternalPdfJobController controller = new InternalPdfJobController(autoSubmit, mock(PdfJobCaseResolver.class), mock(ReviewEventPublisher.class), "");

        assertThat(controller.completed("job-1", "").getStatusCode().value()).isEqualTo(401);
        assertThat(controller.completed("job-1", null).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(autoSubmit);
    }
}
