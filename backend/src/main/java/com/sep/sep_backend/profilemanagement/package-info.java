/**
 * Self-service profile management for the logged-in user.
 *
 * <p>All endpoints live under {@code /api/profile} and require a valid JWT (they sit outside the
 * public {@code /api/account/**} matcher), so the account is always resolved from the authenticated
 * principal rather than from request data. The package lets a user view their privacy details and —
 * after re-confirming ownership with a one-time email code or a matching Google sign-in — change
 * their password or permanently delete their account.
 */
package com.sep.sep_backend.profilemanagement;
