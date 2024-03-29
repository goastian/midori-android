/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.midorinext.android.settings.about

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import org.midorinext.android.BrowserDirection
import org.midorinext.android.BuildConfig
import org.midorinext.android.HomeActivity
import org.midorinext.android.R
import org.midorinext.android.crashes.CrashListActivity
import org.midorinext.android.databinding.FragmentAboutBinding
import org.midorinext.android.ext.settings
import org.midorinext.android.ext.showToolbar
import org.midorinext.android.settings.SupportUtils
import org.midorinext.android.settings.about.AboutItemType.*
import org.midorinext.android.utils.Do
import org.mozilla.geckoview.BuildConfig as GeckoViewBuildConfig

/**
 * Displays the logo and information about the app, including library versions.
 */
class AboutFragment : Fragment() {

    private lateinit var appName: String
    private var _binding: FragmentAboutBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        appName = getString(R.string.app_name)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        lifecycle.addObserver(
            SecretDebugMenuTrigger(
                composeView = binding.aboutContent,
                settings = view.context.settings()
            )
        )

        populateAboutHeader()
        populateAboutList()
    }

    override fun onResume() {
        super.onResume()
        showToolbar(getString(R.string.preferences_about, appName))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun populateAboutHeader() {
        val aboutText = try {
            val packageInfo =
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo).toString()
            val maybeMidoriGitHash =
                if (BuildConfig.GIT_HASH.isNotBlank()) ", ${BuildConfig.GIT_HASH}" else ""
            val componentsAbbreviation = getString(R.string.components_abbreviation)
            val componentsVersion =
                mozilla.components.Build.version + ", " + mozilla.components.Build.gitHash
            val maybeGecko = getString(R.string.gecko_view_abbreviation)
            val geckoVersion =
                GeckoViewBuildConfig.MOZ_APP_VERSION + "-" + GeckoViewBuildConfig.MOZ_APP_BUILDID
            val appServicesAbbreviation = getString(R.string.app_services_abbreviation)
            val appServicesVersion = mozilla.components.Build.applicationServicesVersion

            String.format(
                "%s (Build #%s)%s\n%s: %s\n%s: %s\n%s: %s",
                BuildConfig.VERSION_NAME,
                versionCode,
                maybeMidoriGitHash,
                componentsAbbreviation,
                componentsVersion,
                maybeGecko,
                geckoVersion,
                appServicesAbbreviation,
                appServicesVersion
            )
        } catch (e: PackageManager.NameNotFoundException) {
            ""
        }

        binding.aboutContent.producedByText = getString(R.string.about_content, appName)
        binding.aboutContent.buildVersionsText = aboutText
        binding.aboutContent.buildDateText = BuildConfig.BUILD_DATE
    }

    private fun populateAboutList() {
        val context = requireContext()

        val pageItems = listOf(
            AboutPageItem(
                AboutItem.ExternalLink(
                    SUPPORT,
                    SupportUtils.getSumoURLForTopic(context, SupportUtils.SumoTopic.HELP)
                ),
                getString(R.string.about_support)
            ),
            AboutPageItem(
                AboutItem.Crashes,
                getString(R.string.about_crashes)
            ),
            AboutPageItem(
                AboutItem.ExternalLink(
                    PRIVACY_NOTICE,
                    SupportUtils.getMozillaPageUrl(SupportUtils.MozillaPage.PRIVATE_NOTICE)
                ),
                getString(R.string.about_privacy_notice)
            ),
            AboutPageItem(
                AboutItem.ExternalLink(
                    RIGHTS,
                    SupportUtils.getSumoURLForTopic(context, SupportUtils.SumoTopic.YOUR_RIGHTS)
                ),
                getString(R.string.about_know_your_rights)
            ),
            AboutPageItem(
                AboutItem.ExternalLink(LICENSING_INFO, ABOUT_LICENSE_URL),
                getString(R.string.about_licensing_information)
            ),
            AboutPageItem(
                AboutItem.Libraries,
                getString(R.string.about_other_open_source_libraries)
            )
        )

        binding.aboutContent.pageItems = pageItems
        binding.aboutContent.onPageItemClick = ::onAboutItemClicked
    }

    private fun openLinkInNormalTab(url: String) {
        (activity as HomeActivity).openToBrowserAndLoad(
            searchTermOrURL = url,
            newTab = true,
            from = BrowserDirection.FromAbout
        )
    }

    private fun openLibrariesPage() {
        val navController = findNavController()
        navController.navigate(R.id.action_aboutFragment_to_aboutLibrariesFragment)
    }

    fun onAboutItemClicked(item: AboutItem) {
        Do exhaustive when (item) {
            is AboutItem.ExternalLink -> {
                openLinkInNormalTab(item.url)
            }
            is AboutItem.Libraries -> {
                openLibrariesPage()
            }
            is AboutItem.Crashes -> {
                startActivity(Intent(requireContext(), CrashListActivity::class.java))
            }
        }
    }

    companion object {
        private const val ABOUT_LICENSE_URL = "about:license"
    }
}
