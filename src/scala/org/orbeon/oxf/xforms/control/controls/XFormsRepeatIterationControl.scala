/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control.controls


import org.dom4j.Element
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xml.ContentHandlerHelper
import org.xml.sax.helpers.AttributesImpl
import java.util.Map
import org.orbeon.oxf.xforms.event.events.XXFormsDndEvent
import org.orbeon.oxf.xforms.event.{XFormsEvents, XFormsEvent}
import org.orbeon.oxf.xforms.analysis.controls.RepeatIterationControl
import org.orbeon.oxf.xforms.control.{NoLHHATrait, XFormsControl, XFormsSingleNodeContainerControl}
import org.orbeon.oxf.xforms.BindingContext
import scala.collection.JavaConverters._

/**
 * Represents xforms:repeat iteration information.
 *
 * This is not really a control, but an abstraction for xforms:repeat iterations.
 *
 * TODO: Use inheritance to make this a single-node control that doesn't hold a value.
 */
class XFormsRepeatIterationControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String, state: Map[String, String])
        extends XFormsSingleNodeContainerControl(container, parent, element, effectiveId)
        with NoLHHATrait {

    // Initialize based on the effective id
    private var _iterationIndex = XFormsUtils.getEffectiveIdSuffixParts(effectiveId).lastOption getOrElse -1
    def iterationIndex = _iterationIndex

    // Set a new iteration index. This will cause the nested effective ids to update.
    // This is used to "shuffle" around repeat iterations when repeat nodesets change.
    def setIterationIndex(iterationIndex: Int) {
        if (_iterationIndex != iterationIndex) {
            _iterationIndex = iterationIndex
            updateEffectiveId()
        }
    }

    // Whether this iteration is the repeat's current iteration
    def isCurrentIteration = iterationIndex == repeat.getIndex

    // Des not support refresh events for now (could make sense though)
    override def staticControl = super.staticControl.asInstanceOf[RepeatIterationControl]

    // This iteration's parent repeat control
    def repeat = parent.asInstanceOf[XFormsRepeatControl]

    override def supportsRefreshEvents = false
    override def isStaticReadonly = false
    override def getType = null

    override val getAllowedExternalEvents = Set(XFormsEvents.XXFORMS_REPEAT_ACTIVATE).asJava
    override def supportFullAjaxUpdates = false

    // Update this control's effective id and its descendants based on the parent's effective id.
    override def updateEffectiveId() {
        // Update this iteration's effective id
        setEffectiveId(XFormsUtils.getIterationEffectiveId(parent.getEffectiveId, _iterationIndex))
        children foreach (_.updateEffectiveId())
    }

    override def performDefaultAction(event: XFormsEvent) = event match {
        // For now (2011-12-15), all events reach the repeat iteration instead of the repeat container, except
        // xforms-enabled/xforms-disabled. This might not be the final design, see also:
        // http://wiki.orbeon.com/forms/projects/xforms-repeat-events
        case dndEvent: XXFormsDndEvent ⇒ repeat.doDnD(dndEvent)
        case _ ⇒ super.performDefaultAction(event)
    }

    override def equalsExternal(other: XFormsControl): Boolean = {
        if (other == null || ! (other.isInstanceOf[XFormsRepeatIterationControl]))
            return false

        if (this eq other)
            return true

        val otherRepeatIterationControl = other.asInstanceOf[XFormsRepeatIterationControl]

        // Ad-hoc comparison, because we basically only care about relevance changes
        ! mustSendIterationUpdate(otherRepeatIterationControl)
    }

    private def mustSendIterationUpdate(other: XFormsRepeatIterationControl) = {
        // NOTE: We only care about relevance changes. We should care about moving iterations around, but that's not
        // handled that way yet!

        // NOTE: We output if we are NOT relevant as the client must mark non-relevant elements. Ideally, we should not
        // have non-relevant iterations actually present on the client.
        (other eq null) && ! isRelevant || (other ne null) && other.isRelevant != isRelevant
    }

    override def outputAjaxDiff(ch: ContentHandlerHelper, other: XFormsControl, attributesImpl: AttributesImpl, isNewlyVisibleSubtree: Boolean): Unit = {
        assert(attributesImpl.getLength == 0)
        val repeatIterationControl1 = other.asInstanceOf[XFormsRepeatIterationControl]
        if (mustSendIterationUpdate(repeatIterationControl1)) {
            // Use the effective id of the parent repeat
            attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, parent.getEffectiveId)

            // Relevance
            attributesImpl.addAttribute("", XFormsConstants.RELEVANT_ATTRIBUTE_NAME, XFormsConstants.RELEVANT_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA, isRelevant.toString)
            attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, iterationIndex.toString)
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl)
        }

        // NOTE: in this case, don't do the regular Ajax output (maybe in the future we should to be more consistent?)
    }

    override def pushBindingImpl(parentContext: BindingContext) = {
        // Compute new binding
        val newBindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(parentContext)
            contextStack.pushIteration(iterationIndex)
            contextStack.getCurrentBindingContext
        }

        // Set binding context
        setBindingContext(newBindingContext)

        newBindingContext
    }
}
