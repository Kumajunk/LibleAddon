package net.kumajunk.libleaddon.clickgui

import com.odtheking.odin.OdinMod.mc
import com.odtheking.odin.clickgui.Panel
import com.odtheking.odin.clickgui.SearchBar
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.features.Module
import com.odtheking.odin.features.Category
import com.odtheking.odin.features.ModuleManager
import com.odtheking.odin.features.impl.render.ClickGUIModule
import com.odtheking.odin.utils.Color
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.ui.HoverHandler
import com.odtheking.odin.utils.ui.animations.EaseOutAnimation
import com.odtheking.odin.utils.ui.rendering.NVGRenderer
import com.odtheking.odin.utils.ui.rendering.NVGPIPRenderer
import net.kumajunk.libleaddon.LibleAddon
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.sign
import com.odtheking.odin.utils.ui.mouseX as odinMouseX
import com.odtheking.odin.utils.ui.mouseY as odinMouseY

/**
 * Addon settings GUI - identical to ClickGUI but for addon modules.
 * Accessible via /la or /libleaddon command.
 */
object AddonGUI : Screen(Component.literal("Addon Settings")) {

    private val panels: ArrayList<Panel> = arrayListOf<Panel>().apply {
        if (Category.categories.any { (category, _) -> ClickGUIModule.panelSetting[category] == null }) ClickGUIModule.resetPositions()
        
        // Create panels for all categories, then filter module buttons to only show addon modules
        for ((_, category) in Category.categories) {
            val panel = Panel(category)
            
            try {
                // Use reflection to access and filter the moduleButtons list
                val moduleButtonsField = panel.javaClass.getDeclaredField("moduleButtons")
                moduleButtonsField.isAccessible = true
                
                @Suppress("UNCHECKED_CAST")
                val moduleButtons = moduleButtonsField.get(panel) as? MutableList<*>
                
                if (moduleButtons != null) {
                    // Remove module buttons that don't belong to addon modules
                    moduleButtons.removeIf { button ->
                        try {
                            val moduleField = button?.javaClass?.getDeclaredField("module")
                            moduleField?.isAccessible = true
                            val module = moduleField?.get(button) as? Module
                            // Check if module is in the addon modules set
                            module !in LibleAddon.addonModules
                        } catch (_: Exception) {
                            true // Remove if we can't access the module
                        }
                    }
                    
                    // Only add panel if it has addon modules
                    if (moduleButtons.isNotEmpty()) {
                        add(panel)
                    }
                }
            } catch (e: Exception) {
                // If reflection fails, skip this panel
                println("Failed to filter panel for category ${category}: ${e.message}")
            }
        }
    }

    private var openAnim = EaseOutAnimation(500)
    val gray38 = Color(38, 38, 38)

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        NVGPIPRenderer.draw(context, 0, 0, context.guiWidth(), context.guiHeight()) {
            val scaledMouseX = odinMouseX / ClickGUIModule.getStandardGuiScale()
            val scaledMouseY = odinMouseY / ClickGUIModule.getStandardGuiScale()

            NVGRenderer.scale(ClickGUIModule.getStandardGuiScale(), ClickGUIModule.getStandardGuiScale())

            SearchBar.draw(
                mc.window.width / (2f * ClickGUIModule.getStandardGuiScale()) - 175f,
                (mc.window.height - 110f) / ClickGUIModule.getStandardGuiScale() - 20f,
                scaledMouseX,
                scaledMouseY
            )

            if (openAnim.isAnimating()) {
                val scale = openAnim.get(0f, 1f)

                val centerX = context.guiWidth().toFloat()
                val centerY = context.guiHeight().toFloat()
                NVGRenderer.translate(centerX, centerY)
                NVGRenderer.scale(scale, scale)
                NVGRenderer.translate(-centerX, -centerY)
            }

            val draggedPanel = panels.firstOrNull { it.dragging }
            for (panel in panels) {
                if (panel != draggedPanel) panel.draw(scaledMouseX, scaledMouseY)
            }

            draggedPanel?.draw(scaledMouseX, scaledMouseY)

            desc.render()
        }
        super.extractRenderState(context, mouseX, mouseY, deltaTicks)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        val actualAmount = (verticalAmount.sign * 16).toInt()
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].handleScroll(actualAmount)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(
        mouseButtonEvent: MouseButtonEvent,
        bl: Boolean
    ): Boolean {
        val scaledMouseX = odinMouseX / ClickGUIModule.getStandardGuiScale()
        val scaledMouseY = odinMouseY / ClickGUIModule.getStandardGuiScale()
        SearchBar.mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].mouseClicked(scaledMouseX, scaledMouseY, mouseButtonEvent)) return true
        }
        return super.mouseClicked(mouseButtonEvent, bl)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        SearchBar.mouseReleased()
        for (i in panels.size - 1 downTo 0) {
            panels[i].mouseReleased(mouseButtonEvent)
        }
        return super.mouseReleased(mouseButtonEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        SearchBar.keyTyped(characterEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyTyped(characterEvent)) return true
        }
        return super.charTyped(characterEvent)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        SearchBar.keyPressed(keyEvent)
        for (i in panels.size - 1 downTo 0) {
            if (panels[i].keyPressed(keyEvent)) return true
        }
        return super.keyPressed(keyEvent)
    }

    override fun init() {
        openAnim.start()
        super.init()
    }

    override fun onClose() {
        for (panel in panels.filter { it.panelSetting.extended }.reversed()) {
            for (moduleButton in panel.moduleButtons.filter { it.extended }) {
                for (setting in moduleButton.representableSettings) {
                    if (setting is ColorSetting) setting.section = null
                    setting.listening = false
                }
            }
        }

        ModuleManager.saveConfigurations()
        super.onClose()
    }

    override fun isPauseScreen(): Boolean = false

    private var desc = Description("", 0f, 0f, HoverHandler(150))

    data class Description(var text: String, var x: Float, var y: Float, var hoverHandler: HoverHandler) {

        fun render() {
            if (text.isEmpty() || hoverHandler.percent() < 100) return
            val area = NVGRenderer.wrappedTextBounds(text, 300f, 16f, NVGRenderer.defaultFont)
            NVGRenderer.rect(x, y, area[2] - area[0] + 16f, area[3] - area[1] + 16f, gray38.rgba, 5f)
            NVGRenderer.hollowRect(
                x,
                y,
                area[2] - area[0] + 16f,
                area[3] - area[1] + 16f,
                1.5f,
                ClickGUIModule.clickGUIColor.rgba,
                5f
            )
            NVGRenderer.drawWrappedString(text, x + 8f, y + 8f, 300f, 16f, Colors.WHITE.rgba, NVGRenderer.defaultFont)
        }
    }
}
