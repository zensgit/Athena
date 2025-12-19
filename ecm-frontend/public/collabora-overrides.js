/*
  Collabora Online runtime overrides (same-origin via ecm-frontend nginx).

  NOTE:
  - Collabora CODE is not a white-label product. Removing/altering vendor
    branding or disabling vendor UX flows may violate Collabora's terms/
    trademarks. Use this only for local dev/testing.
*/

(function collaboraOverrides() {
  function disableWelcomeAndFeedback() {
    const welcome = document.getElementById('init-welcome-url');
    if (welcome) {
      welcome.value = '';
      welcome.setAttribute('value', '');
    }

    const feedback = document.getElementById('init-feedback-url');
    if (feedback) {
      feedback.value = '';
      feedback.setAttribute('value', '');
    }

    try {
      // Best-effort: some Collabora builds read these globals.
      // Keep falsy so any "if (welcomeUrl)" checks skip.
      // eslint-disable-next-line no-undef
      window.welcomeUrl = '';
      // eslint-disable-next-line no-undef
      window.feedbackUrl = '';
    } catch {
      // ignore
    }

    return Boolean(welcome && feedback);
  }

  // Run early (head-injected) and keep watching briefly in case the inputs
  // appear later during parsing.
  const stopAt = Date.now() + 10_000;
  const observer = new MutationObserver(() => {
    if (disableWelcomeAndFeedback() || Date.now() > stopAt) {
      observer.disconnect();
    }
  });

  try {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  } catch {
    // ignore
  }

  disableWelcomeAndFeedback();
  document.addEventListener('DOMContentLoaded', () => {
    disableWelcomeAndFeedback();
    observer.disconnect();
  });
})();

