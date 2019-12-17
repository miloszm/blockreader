import com.google.inject.AbstractModule
import java.time.Clock

import connectors.{AkkaHttpClient, HttpClient, MockHttpClient}
import net.sf.ehcache.Ehcache
import net.sf.ehcache.hibernate.EhCache
import play.api.cache.CacheApi
import services.{ApplicationTimer, AtomicCounter, Counter, GlobalScheduler}

/**
 * This class is a Guice module that tells Guice how to bind several
 * different types. This Guice module is created when the Play
 * application starts.

 * Play will automatically use any class called `Module` that is in
 * the root package. You can create modules in other locations by
 * adding `play.modules.enabled` settings to the `application.conf`
 * configuration file.
 */
class Module extends AbstractModule {

  override def configure() = {
    // Use the system clock as the default implementation of Clock
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone)
    // Ask Guice to create an instance of ApplicationTimer when the
    // application starts.
    bind(classOf[ApplicationTimer]).asEagerSingleton()
    // Set AtomicCounter as the implementation for Counter.
    bind(classOf[Counter]).to(classOf[AtomicCounter])

    bind(classOf[GlobalScheduler]).asEagerSingleton()

    bind(classOf[HttpClient]).toInstance(AkkaHttpClient)
  }

}
