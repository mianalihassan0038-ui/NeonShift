using System;
using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEngine;

namespace NeonShift.EditorTools
{
    /// <summary>
    /// Command-line Android build, used by GitHub Actions (GameCI) and usable locally:
    ///   Unity -batchmode -quit -projectPath . -executeMethod NeonShift.EditorTools.BuildScript.BuildAndroid
    /// Optional args: -customBuildPath path/to/NeonShift.apk   (GameCI passes this)
    ///                -androidAab                              (build an .aab for the Play Store)
    /// </summary>
    public static class BuildScript
    {
        public const string PackageName = "com.neonshift.game";
        public const string IconPath = "Assets/Art/Icons/AppIcon.png";

        [MenuItem("NEON SHIFT/Build Android APK")]
        public static void BuildAndroidMenu() { Build(false, "Builds/Android/NeonShift.apk", false); }

        public static void BuildAndroid()
        {
            bool aab = HasArg("-androidAab") || GetArg("-androidExportType") == "androidAppBundle";
            string path = GetArg("-customBuildPath") ?? (aab ? "Builds/Android/NeonShift.aab" : "Builds/Android/NeonShift.apk");
            Build(aab, path, true);
        }

        static void Build(bool appBundle, string path, bool exitOnFinish)
        {
            NeonShiftProjectSetup.ApplyPlayerSettings();
            NeonShiftProjectSetup.EnsureGameplayScene();

            var android = NamedBuildTarget.Android;
            PlayerSettings.companyName = "NeonShift";
            PlayerSettings.SetApplicationIdentifier(android, PackageName);
            PlayerSettings.bundleVersion = Env("NEONSHIFT_VERSION", "0.4.0");
            int code;
            PlayerSettings.Android.bundleVersionCode = int.TryParse(Env("GITHUB_RUN_NUMBER", "1"), out code) ? code : 1;
            PlayerSettings.SetScriptingBackend(android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64 | AndroidArchitecture.ARMv7;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel23;
            PlayerSettings.SplashScreen.backgroundColor = new Color(9f / 255f, 13f / 255f, 27f / 255f);

            var icon = AssetDatabase.LoadAssetAtPath<Texture2D>(IconPath);
            if (icon != null) PlayerSettings.SetIcons(NamedBuildTarget.Unknown, new[] { icon }, IconKind.Any);
            else Debug.LogWarning("NEON SHIFT: icon not found at " + IconPath);

            EditorUserBuildSettings.buildAppBundle = appBundle;
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(path)));

            var options = new BuildPlayerOptions
            {
                scenes = new[] { NeonShiftProjectSetup.ScenePath },
                locationPathName = path,
                target = BuildTarget.Android,
                targetGroup = BuildTargetGroup.Android,
                options = BuildOptions.None
            };
            BuildReport report = BuildPipeline.BuildPlayer(options);
            BuildSummary summary = report.summary;
            Debug.Log("NEON SHIFT build: " + summary.result + ", " + summary.totalSize / (1024 * 1024) + " MB, " + summary.totalErrors + " errors -> " + path);

            if (exitOnFinish) EditorApplication.Exit(summary.result == BuildResult.Succeeded ? 0 : 1);
        }

        static string Env(string name, string fallback)
        {
            string v = Environment.GetEnvironmentVariable(name);
            return string.IsNullOrEmpty(v) ? fallback : v;
        }

        static bool HasArg(string name) { return Array.IndexOf(Environment.GetCommandLineArgs(), name) >= 0; }

        static string GetArg(string name)
        {
            string[] args = Environment.GetCommandLineArgs();
            int i = Array.IndexOf(args, name);
            return i >= 0 && i + 1 < args.Length ? args[i + 1] : null;
        }
    }
}
