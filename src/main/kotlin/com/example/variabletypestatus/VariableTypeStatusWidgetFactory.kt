package com.example.variabletypestatus

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class VariableTypeStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "VariableTypeStatusWidget"

    override fun getDisplayName(): String = "Variable Type Inspector"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = VariableTypeStatusWidget(project)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
