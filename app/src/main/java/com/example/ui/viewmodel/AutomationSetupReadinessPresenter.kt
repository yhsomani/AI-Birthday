package com.example.ui.viewmodel

import android.content.Context
import androidx.annotation.StringRes
import com.example.R
import com.example.domain.automation.SetupReadinessRecommendationCandidate
import com.example.domain.automation.SetupReadinessRecommendationPolicy
import com.example.domain.automation.SetupReadinessSummaryPolicy
import com.example.domain.readiness.RelationshipActionReadiness
import com.example.domain.readiness.RelationshipActionReadinessPolicy

internal class AutomationSetupReadinessPresenter(
    private val context: Context,
) {
    fun summarize(checks: List<ReadinessCheck>): AiDoctorSummary {
        val decision = SetupReadinessSummaryPolicy.summarize(checks.map { it.status })
        val firstProblem = decision.firstProblemIndex?.let(checks::get)

        return when (decision.status) {
            ReadinessStatus.ACTION_REQUIRED -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_blockers, decision.blockerCount),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_start_with, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_required),
                status = ReadinessStatus.ACTION_REQUIRED,
            )

            ReadinessStatus.WARNING -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_warnings),
                detail = firstProblem?.let {
                    text(R.string.automation_setup_summary_problem, it.title, it.detail)
                } ?: text(R.string.automation_setup_summary_review_warnings),
                status = ReadinessStatus.WARNING,
            )

            ReadinessStatus.OK -> AiDoctorSummary(
                title = text(R.string.automation_setup_summary_ok),
                detail = text(R.string.automation_setup_summary_ok_detail),
                status = ReadinessStatus.OK,
            )
        }
    }

    fun recommendedFix(checks: List<ReadinessCheck>): AiDoctorRecommendedFix? {
        val index = SetupReadinessRecommendationPolicy.selectRecommendedIndex(
            checks.toRecommendationCandidates(),
        ) ?: return null
        val check = checks[index]
        return AiDoctorRecommendedFix(
            title = check.title,
            detail = check.detail,
            actionLabel = check.actionLabel.orEmpty(),
            action = check.action,
            status = check.status,
            group = check.group,
            actionReadiness = check.actionReadiness,
        )
    }

    fun setupProgress(checks: List<ReadinessCheck>): SetupProgressSummary {
        return checks.toSetupProgressSummary()
    }

    fun setupActionReadiness(checks: List<ReadinessCheck>): RelationshipActionReadiness {
        return RelationshipActionReadinessPolicy.fromSetupCandidates(checks.toRecommendationCandidates())
    }

    private fun List<ReadinessCheck>.toRecommendationCandidates(): List<SetupReadinessRecommendationCandidate> {
        return map { check ->
            SetupReadinessRecommendationCandidate(
                status = check.status,
                group = check.group,
                hasAction = check.action != AiDoctorAction.NONE && !check.actionLabel.isNullOrBlank(),
            )
        }
    }

    private fun text(@StringRes resId: Int, vararg args: Any): String {
        return context.getString(resId, *args)
    }
}
