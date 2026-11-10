package com.splashcleaner.domain

/** The 23 checks and their thresholds are transcribed from the supplied inspection standard. */
object InspectionCatalog {
    val checks = listOf(
        CheckDefinition("A-01", "广告标识是否存在", Severity.MEDIUM, "半自动", "广告/赞助/推广/AD 标识持续可见 ≥ 1000ms", "首帧截图 + 节点树 + 时间戳"),
        CheckDefinition("A-02", "标识是否真的看得清", Severity.HIGH, "半自动", "文字高度 ≥ 10dp、对比度 ≥ 3:1 且不被遮挡", "放大截图 + 采样坐标 + 对比度值"),
        CheckDefinition("B-01", "关闭标志是否存在", Severity.SERIOUS, "自动", "广告区域存在可点击关闭控件", "节点树 + 控件 bounds"),
        CheckDefinition("B-02", "是否必须等倒计时结束", Severity.SERIOUS, "自动", "关闭控件不可用/透明度低于 50% 持续超过 1000ms", "状态时间序列 + 两帧截图"),
        CheckDefinition("B-03", "是否需要两次以上点击", Severity.SERIOUS, "自动", "一次点击后广告关闭，不出现确认/挽留层", "点击前后截图 + 窗口事件"),
        CheckDefinition("B-04", "关闭标志是否真实", Severity.SERIOUS, "自动", "点击后停留原应用，不跳第三方或商店", "前后包名 + 目标页面截图"),
        CheckDefinition("B-05", "关闭标志是否够大、够得着", Severity.HIGH, "自动", "可视区 ≥ 24×24dp、热区 ≥ 44×44dp、距边 ≥ 8dp", "bounds 数值 + 标注截图；工程口径"),
        CheckDefinition("B-06", "关闭标志对比度", Severity.HIGH, "半自动", "图标与相邻背景对比度 ≥ 3:1", "采样点坐标 + 对比度值 + 原始截图；工程口径"),
        CheckDefinition("B-07", "关闭后是否继续弹", Severity.SERIOUS, "自动", "关闭后 1500ms 内同页再次出现全屏/半屏广告", "事件序列 + 第二枚广告截图"),
        CheckDefinition("B-08", "开屏时长与可跳过性", Severity.MEDIUM, "自动", "可关闭 ≤ 3000ms，总展示 ≤ 5000ms；工程口径", "首帧与可点帧时间差"),
        CheckDefinition("C-01", "第二个“关闭”是陷阱", Severity.HIGH, "自动", "疑似关闭控件点击后跳转第三方/落地页", "控件列表 + 前后包名"),
        CheckDefinition("C-02", "假系统提示", Severity.HIGH, "半自动", "系统语义文案命中且点击进入广告页", "截图 + 文案命中记录 + 点击后截图"),
        CheckDefinition("C-03", "假奖励承诺", Severity.MEDIUM, "半自动", "奖励承诺在 3 次交互内无法抵达兑现路径", "截图 + 交互路径录屏"),
        CheckDefinition("C-04", "整屏都能点", Severity.HIGH, "自动", "单个可点击节点面积 ≥ 屏幕 60% 或无非点击区", "空间树 + 命中率统计"),
        CheckDefinition("C-05", "仿系统控件", Severity.MEDIUM, "人工", "仿系统控件的点击行为与图形暗示不一致", "截图 + 系统控件对照图"),
        CheckDefinition("D-01", "摇动引导是否明示", Severity.MEDIUM, "半自动", "有摇动文字/图标，且对比度 ≥ 3:1", "截图 + 对比度采样"),
        CheckDefinition("D-02", "是否说明会跳到哪里", Severity.MEDIUM, "半自动", "事前说明跳转结果且与实际目标一致", "截图 + 跳转目标包名"),
        CheckDefinition("D-03", "触发阈值是否达标", Severity.SERIOUS, "人工", "日常动作各 10 次，误触发 0 次；须实测", "动作脚本 + 误触发计数 + 设备参数"),
        CheckDefinition("D-04", "能不能关掉摇一摇", Severity.MEDIUM, "人工", "设置两级菜单可达关闭项，关闭后重启不再出现", "设置页截图 + 前后对比"),
        CheckDefinition("D-05", "跳转后是否强制下载", Severity.HIGH, "人工+自动", "未经再次确认自动下载或拉起商店安装页", "下载记录 + 跳转后截图"),
        CheckDefinition("E-01", "单次会话广告频次", Severity.MEDIUM, "自动", "单次会话全屏广告 ≥ 3 次计入问题", "会话时间轴 + 每枚广告截图"),
        CheckDefinition("E-02", "是否堵死首屏", Severity.MEDIUM, "自动", "首屏占比 ≥ 80%，且持续 > 5000ms 或全程不可关闭", "首屏截图 + 面积计算"),
        CheckDefinition("E-03", "是否在操作中打断", Severity.MEDIUM, "自动", "输入聚焦/惯性滚动/视频播放时弹全屏广告", "事件对齐表 + 截图")
    )
}
