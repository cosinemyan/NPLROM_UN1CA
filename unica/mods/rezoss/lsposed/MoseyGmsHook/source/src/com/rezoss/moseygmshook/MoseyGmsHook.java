package com.rezoss.moseygmshook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Collections;
import java.util.List;

public final class MoseyGmsHook implements IXposedHookLoadPackage {
    private static final String TAG = "MoseyGmsHook";
    private static final String FORCE_CHANNEL_ONE_PROP = "persist.unica.mosey.force_channel_one";
    private static final Integer S23U_TEST_CHANNEL = Integer.valueOf(1);
    private static volatile boolean tidepoolHooked;
    private static volatile boolean externalProviderHooked;
    private static volatile boolean currentExternalProviderRegistryHooked;
    private static volatile boolean moseyChannelHooked;
    private static volatile boolean shareLiveProtocolXHooked;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.samsung.android.app.sharelive".equals(lpparam.packageName)) {
            hookShareLiveProtocolX(lpparam.classLoader, lpparam.processName);
            return;
        }

        if ("com.google.android.mosey".equals(lpparam.packageName)) {
            hookMoseyChannelOverride(lpparam.classLoader, lpparam.processName);
            return;
        }

        if (!"com.google.android.gms".equals(lpparam.packageName)) {
            return;
        }

        String processName = lpparam.processName == null ? "" : lpparam.processName;
        if (!"com.google.android.gms".equals(processName)
                && !"com.google.android.gms.persistent".equals(processName)
                && !"com.google.android.gms.unstable".equals(processName)) {
            return;
        }

        hookTidepoolGate(lpparam.classLoader, processName);
        hookExternalProviderGate(lpparam.classLoader, processName);
        hookCurrentExternalProviderRegistryGate(lpparam.classLoader, processName);
    }

    private static void hookShareLiveProtocolX(ClassLoader classLoader, String processName) {
        if (shareLiveProtocolXHooked) {
            return;
        }

        boolean hooked = false;

        hooked |= hookShareLiveProtocolXMethod(
                classLoader,
                processName,
                "vf.f5",
                "lw.u",
                "i",
                "ShareLive ProtocolX support");

        hooked |= hookShareLiveProtocolXMethod(
                classLoader,
                processName,
                "yf.l5",
                "zw.u",
                "j",
                "legacy ShareLive ProtocolX support");

        shareLiveProtocolXHooked = hooked;
    }

    private static boolean hookShareLiveProtocolXMethod(
            ClassLoader classLoader,
            String processName,
            String repositoryClassName,
            String reactiveClassName,
            String factoryMethodName,
            String description) {
        try {
            final Class<?> reactiveValueClass = XposedHelpers.findClass(reactiveClassName, classLoader);
            XposedHelpers.findAndHookMethod(
                    repositoryClassName,
                    classLoader,
                    "e",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Object result = XposedHelpers.callStaticMethod(
                                    reactiveValueClass, factoryMethodName, Boolean.TRUE);
                            param.setResult(result);
                        }
                    });
            XposedBridge.log(TAG + ": hooked " + description + " "
                    + repositoryClassName + ".e() via " + reactiveClassName + "."
                    + factoryMethodName + "() in " + processName);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook " + description + " "
                    + repositoryClassName + ".e() in " + processName + ": " + t);
            return false;
        }
    }

    private static void hookTidepoolGate(ClassLoader classLoader, String processName) {
        if (tidepoolHooked) {
            return;
        }

        boolean hooked = false;

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "duwd",
                "p",
                "GMS Tidepool gate");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "dulb",
                "p",
                "legacy GMS Tidepool gate");

        tidepoolHooked = hooked;
    }

    private static void hookExternalProviderGate(ClassLoader classLoader, String processName) {
        if (externalProviderHooked) {
            return;
        }

        boolean hooked = false;

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyzi",
                "v",
                "GMS Nearby external-provider gate");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jybb",
                "T",
                "legacy GMS Nearby external-provider gate");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyaz",
                "v",
                "legacy GMS Nearby external-provider wrapper");

        externalProviderHooked = hooked;
    }

    private static void hookCurrentExternalProviderRegistryGate(
            ClassLoader classLoader, String processName) {
        if (currentExternalProviderRegistryHooked) {
            return;
        }

        boolean hooked = false;

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "dvxw",
                "mB",
                "current GMS external-provider direct supplier");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "dvxy",
                "mB",
                "current GMS external-provider registry supplier");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyzk",
                "V",
                "current GMS Nearby external-provider implementation gate");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyzn",
                "aX",
                "current GMS external-provider registry feature flag");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyzn",
                "aH",
                "current GMS provider-version metadata flag");

        hooked |= hookBooleanMethod(
                classLoader,
                processName,
                "jyzn",
                "be",
                "current GMS external-provider registry start flag");

        currentExternalProviderRegistryHooked = hooked;
    }

    private static boolean hookBooleanMethod(
            ClassLoader classLoader,
            String processName,
            String className,
            String methodName,
            String description) {
        try {
            XposedHelpers.findAndHookMethod(
                    className,
                    classLoader,
                    methodName,
                    XC_MethodReplacement.returnConstant(Boolean.TRUE));
            XposedBridge.log(TAG + ": hooked " + description + " "
                    + className + "." + methodName + "() in " + processName);
            return true;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook " + description + " "
                    + className + "." + methodName + "() in " + processName + ": " + t);
            return false;
        }
    }

    private static void hookMoseyChannelOverride(ClassLoader classLoader, String processName) {
        if (moseyChannelHooked) {
            return;
        }

        try {
            Class<?> continuationClass = XposedHelpers.findClass("dcz", classLoader);
            XposedHelpers.findAndHookMethod(
                    "boe",
                    classLoader,
                    "c",
                    continuationClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (!isMoseyChannelOneOverrideEnabled()) {
                                return;
                            }

                            Object result = param.getResult();
                            if (!(result instanceof List)) {
                                return;
                            }

                            param.setResult(Collections.singletonList(S23U_TEST_CHANNEL));
                            XposedBridge.log(TAG + ": forced Mosey preferred channels from "
                                    + result + " to [1] in " + processName);
                        }
                    });
            moseyChannelHooked = true;
            XposedBridge.log(TAG + ": hooked Mosey preferred-channel override boe.c() in "
                    + processName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook Mosey preferred-channel override in "
                    + processName + ": " + t);
        }
    }

    private static boolean isMoseyChannelOneOverrideEnabled() {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Object value = systemProperties
                    .getMethod("get", String.class, String.class)
                    .invoke(null, FORCE_CHANNEL_ONE_PROP, "0");
            return "1".equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
        } catch (Throwable t) {
            return false;
        }
    }
}
