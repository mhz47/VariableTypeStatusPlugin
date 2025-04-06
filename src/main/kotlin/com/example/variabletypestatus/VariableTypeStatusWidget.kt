package com.example.variabletypestatus

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Consumer
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.TypeEvalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.MouseEvent

class VariableTypeStatusWidget(private val project: Project) : StatusBarWidget {
    private var baseStr = "Type: "
    private var noType = "-"
    private var currentType: String = baseStr + noType
    private var statusBar: StatusBar? = null

    override fun ID(): String = "VariableTypeStatusWidget"

    override fun getPresentation(): StatusBarWidget.WidgetPresentation {
        return object : StatusBarWidget.TextPresentation {
            override fun getText(): String = currentType

            override fun getAlignment(): Float = 0.0f

            override fun getTooltipText(): String = "Displays the type of the variable under caret."

            override fun getClickConsumer(): Consumer<MouseEvent>? {
                return Consumer { mouseEvent ->
                    println("Widget clicked at: ${mouseEvent.point}")
                }
            }
        }
    }

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    override fun dispose() {
        statusBar = null
    }

    init {
        registerCaretListener()
    }

    private fun registerCaretListener() {
        val editorEventMulticaster = EditorFactory.getInstance().eventMulticaster
        editorEventMulticaster.addCaretListener(
            object : CaretListener {
                override fun caretPositionChanged(event: CaretEvent) {
                    update(event.editor)
                }
            },
            project,
        )
    }

    private fun changeStatusTo(
        type: String,
        info: String? = null,
    ) {
        currentType = baseStr + type.split(" | ").distinct().joinToString(" | ")
        if (info != null && info.isNotBlank()) {
            currentType += " [$info]"
        }
        statusBar?.updateWidget(ID())
    }

    private class Type(val name: String, val info: String? = null)

    private fun resolveType(
        element: PsiElement,
        context: TypeEvalContext,
    ): Type {
        // keyword
        if (element.node.elementType.toString().contains("KEYWORD")) {
            return Type("keyword")
        }

        // identifier
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java)
        if (pyClass != null && element == pyClass.nameIdentifier) {
            return Type(pyClass.name.toString())
        }
        val pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction::class.java)
        if (pyFunction != null) {
            if (element == pyFunction.nameIdentifier) {
                return Type("function", context.getReturnType(pyFunction)?.name ?: "unknown")
            } else if (element.text == "self") {
                val containingClass = pyFunction.containingClass
                if (containingClass != null) {
                    val selfType = context.getType(containingClass)?.name ?: containingClass.name ?: "unknown"
                    return Type(selfType)
                }
            }
        }

        // variable
        val targetExpression = PsiTreeUtil.getParentOfType(element, PyTargetExpression::class.java)
        if (targetExpression != null) {
            val assignedValue = targetExpression.findAssignedValue()
            return if (assignedValue != null) {
                Type(context.getType(assignedValue)?.name ?: "unknown")
            } else {
                Type(noType, "no value assigned")
            }
        }

        // reference
        val referenceExpression = PsiTreeUtil.getParentOfType(element, PyReferenceExpression::class.java)
        if (referenceExpression != null) {
            val resolvedElement = referenceExpression.reference.resolve()
            return if (resolvedElement is PyFunction) {
                Type("function", context.getReturnType(resolvedElement)?.name ?: "unknown")
            } else {
                Type(context.getType(referenceExpression)?.name ?: "unknown")
            }
        }

        return Type(noType)
    }

    private fun update(editor: Editor?) {
        if (editor == null || project.isDisposed) {
            changeStatusTo(noType)
            return
        }

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            val type =
                withContext(Dispatchers.Default) {
                    ApplicationManager.getApplication().runReadAction<Type> {
                        val offset = editor.caretModel.offset
                        val document = editor.document
                        val psiFile =
                            PsiDocumentManager.getInstance(project).getPsiFile(document) as? PyFile ?: run {
                                return@runReadAction Type(noType, "Not a Python file")
                            }
                        val element = psiFile.findElementAt(offset)
                        if (element == null) {
                            return@runReadAction Type(noType)
                        }
                        return@runReadAction resolveType(element, TypeEvalContext.codeAnalysis(project, psiFile))
                    }
                }
            withContext(Dispatchers.Main) {
                changeStatusTo(type.name, type.info)
            }
        }
    }
}
