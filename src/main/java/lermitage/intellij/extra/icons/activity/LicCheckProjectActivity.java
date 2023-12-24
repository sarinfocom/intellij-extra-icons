// SPDX-License-Identifier: MIT

package lermitage.intellij.extra.icons.activity;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import lermitage.intellij.extra.icons.BaseIconProvider;
import lermitage.intellij.extra.icons.lic.ExtraIconsLicenseCheck;
import lermitage.intellij.extra.icons.lic.ExtraIconsLicenseStatus;
import lermitage.intellij.extra.icons.lic.ExtraIconsPluginType;
import lermitage.intellij.extra.icons.messaging.RefreshIconsNotifierService;
import lermitage.intellij.extra.icons.utils.I18nUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

/**
 * Check licence periodically.
 */
public class LicCheckProjectActivity implements ProjectActivity {

    private static final @NonNls Logger LOGGER = Logger.getInstance(BaseIconProvider.class);

    private static final ResourceBundle i18n = I18nUtils.getResourceBundle();

    @Nullable
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        int check_delay = 30_000; // 30 sec
        int check_period = 3_600_000; // 1 hr
        if ("true".equals(System.getenv("EXTRA_ICONS_TEST_MODE"))) {
            check_delay = 3_000; // 3 sec
            check_period = 240_000; // 4 min
        }

        long t1 = System.currentTimeMillis();
        ExtraIconsPluginType installedPluginType = findInstalledPluginType();
        long t2 = System.currentTimeMillis();
        LOGGER.info("Found Extra Icons configured for license type in " + (t2 - t1) + " ms: " + installedPluginType);

        if (installedPluginType.isRequiresLicense()) {
            try {
                ExtraIconsLicenseStatus.setLicenseActivated(true);
                LOGGER.info("Will check Extra Icons license in " + check_delay / 1000 + " sec, then every " + check_period / 1000 + " sec");
                Timer timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        long t1 = System.currentTimeMillis();
                        Boolean isLicensed = ExtraIconsLicenseCheck.isLicensed(installedPluginType.getProductCode());
                        long t2 = System.currentTimeMillis();
                        LOGGER.warn("Checked Extra Icons license in " + (t2 - t1) + " ms. User has a valid license: " + isLicensed);
                        if (isLicensed == null) {
                            LOGGER.warn("Extra Icons license check returned null. Ignoring for now");
                        }
                        if (isLicensed != null && !isLicensed) {
                            ExtraIconsLicenseStatus.setLicenseActivated(false);
                            LOGGER.warn("Failed to validate Extra Icons license. Disable all Extra Icons until license activation");
                            RefreshIconsNotifierService.getInstance().triggerAllIconsRefreshAndIconEnablersReinit();
                            ExtraIconsLicenseCheck.requestLicense(installedPluginType.getProductCode(), i18n.getString("license.required.msg"));
                        }
                    }
                }, check_delay, check_period);
            } catch (Exception e) {
                LOGGER.warn(e);
            }
        }
        return null;
    }

    private ExtraIconsPluginType findInstalledPluginType() {
        PluginDescriptor pluginDesc = PluginManager.getPluginByClass(LicCheckProjectActivity.class);
        if (pluginDesc == null) {
            LOGGER.warn("Failed to find installed Extra Icons plugin by class, will list all installed plugins and try to find it");
            Set<String> registeredIds = PluginId.getRegisteredIds().stream()
                .map(PluginId::getIdString)
                .collect(Collectors.toSet());
            Optional<ExtraIconsPluginType> extraIconsPluginTypeFound = ExtraIconsPluginType.FINDABLE_TYPES.stream()
                .filter(extraIconsPluginType -> registeredIds.contains(extraIconsPluginType.getPluginId()))
                .findFirst();
            return extraIconsPluginTypeFound.orElse(ExtraIconsPluginType.NOT_FOUND);
        } else {
            LOGGER.info("Found installed Extra Icons plugin by class: " + pluginDesc);
            String installedPluginId = pluginDesc.getPluginId().getIdString();
            Optional<ExtraIconsPluginType> extraIconsPluginTypeFound = ExtraIconsPluginType.FINDABLE_TYPES.stream()
                .filter(extraIconsPluginType -> installedPluginId.equals(extraIconsPluginType.getPluginId()))
                .findFirst();
            return extraIconsPluginTypeFound.orElse(ExtraIconsPluginType.NOT_FOUND);
        }
    }
}