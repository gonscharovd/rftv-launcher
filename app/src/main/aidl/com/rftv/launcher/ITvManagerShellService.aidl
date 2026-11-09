package com.rftv.launcher;

interface ITvManagerShellService {
    void destroy() = 16777114;
    int getUid();
    String setEnabledSetting(String packageName, int enabledState, int userId);
}
