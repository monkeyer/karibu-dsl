package com.github.karibu.testing

import com.vaadin.flow.component.Component
import com.vaadin.flow.component.UI
import com.vaadin.flow.internal.CurrentInstance
import com.vaadin.flow.server.*
import com.vaadin.flow.server.startup.RouteRegistry
import com.vaadin.flow.shared.VaadinUriResolver
import org.atmosphere.util.FakeHttpSession
import java.io.BufferedReader
import java.security.Principal
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.servlet.*
import javax.servlet.http.*

object MockVaadin {
    // prevent GC on Vaadin Session and Vaadin UI as they are only soft-referenced from the Vaadin itself.
    private val strongRefSession = ThreadLocal<VaadinSession>()
    private val strongRefUI = ThreadLocal<UI>()
    private val strongRefReq = ThreadLocal<VaadinRequest>()

    /**
     * Mocks Vaadin for the current test method.
     * @param routes all classes annotated with [com.vaadin.flow.router.Route]; use [autoDiscoverViews] to auto-discover all such classes.
     */
    fun setup(routes: Set<Class<out Component>> = setOf(), uiFactory: ()->UI = { UI() }) {
        val service = object : VaadinServletService(null, DefaultDeploymentConfiguration(MockVaadin::class.java, Properties(), { _, _ -> })) {
            private val registry = object : RouteRegistry() {
                init {
                    setNavigationTargets(routes)
                }
            }
            override fun isAtmosphereAvailable(): Boolean {
                // returning true here would access our null servlet, and we don't want that :)
                return false
            }
            override fun getRouteRegistry(): RouteRegistry = registry

            override fun getMainDivId(session: VaadinSession?, request: VaadinRequest?): String = "ROOT-1"
        }
        service.init()
        VaadinService.setCurrent(service)
        val session = object : VaadinSession(service) {
            private val lock = ReentrantLock().apply { lock() }
            override fun getLockInstance(): Lock = lock
        }
        VaadinSession.setCurrent(session)
        strongRefSession.set(session)
        session.setAttribute(VaadinUriResolverFactory::class.java, MockResolverFactory)

        val ctx = MockContext()
        val request = VaadinServletRequest(MockRequest(ctx), service)
        strongRefReq.set(request)
        CurrentInstance.set(VaadinRequest::class.java, request)

        val ui = uiFactory()
        ui.internals.session = session
        UI.setCurrent(ui)
        ui.doInit(request, -1)
        strongRefUI.set(ui)
    }
}

object MockResolverFactory : VaadinUriResolverFactory {
    override fun getUriResolver(request: VaadinRequest): VaadinUriResolver = MockUriResolver
}

object MockUriResolver : VaadinUriResolver() {
    override fun getFrontendRootUrl(): String = ""
    override fun getContextRootUrl(): String = ""
}