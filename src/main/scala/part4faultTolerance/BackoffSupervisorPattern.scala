package part4faultTolerance

import java.io.File

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorLogging, ActorSystem, OneForOneStrategy, Props}
import akka.pattern.{Backoff, BackoffOpts, BackoffSupervisor}

import scala.io.Source
import scala.concurrent.duration._

object BackoffSupervisorPattern extends App {

  case object ReadFile
  class FileBasedPersistentActor extends Actor with ActorLogging {

    var dataSource: Source = null

    override def preStart(): Unit = log.info("Persistent actor starting")

    override def postStop(): Unit = log.warning("Persistent actor has stopped")

    override def preRestart(reason: Throwable, message: Option[Any]): Unit =
      log.warning("Persistent actor restarting")

    override def receive: Receive = {
      case ReadFile =>
        if (dataSource == null)
          dataSource = Source.fromFile(new File("src/main/resources/testFiles/important.txt"))
        log.info("I've just read some IMPORTANT data: " + dataSource.getLines().toList)
    }
  }

  val system = ActorSystem("BackoffSupervisorDemo")
//  val simpleActor = system.actorOf(Props[FileBasedPersistentActor], "simpleActor")
//  simpleActor ! ReadFile

  val simpleSupervisorProps = BackoffSupervisor.props(
    BackoffOpts
      .onFailure(
        Props[FileBasedPersistentActor],
        "simpleBackoffActor",
        3.seconds, // then 6s, 12,s, 24s
        30.seconds,
        0.2 // so huge amount of actors are not restarting at exact time
      )
  )

//  val simpleBackoffSupervisor = system.actorOf(simpleSupervisorProps, "simpleSupervisor")
//  simpleBackoffSupervisor ! ReadFile // forwarded to child



  /*
    simpleSupervisor
      - child called simpleBackoffActor (props of type FileBasedPersistentActor)
      - supervision strategy is the default one (restarting on everything)
        - first attempt after 3 seconds
        - next attempt is 2x the previous attempt
   */

  val stopSupervisorProps = BackoffSupervisor.props(
    BackoffOpts
      .onStop(
        Props[FileBasedPersistentActor],
        "stopBackoffActor",
        3.seconds,
        30.seconds,
        0.2
      )
      .withSupervisorStrategy(
        OneForOneStrategy() {
          case _ => Stop
        }
      )
  )

//  val stopSupervisor = system.actorOf(stopSupervisorProps, "stopSupervisor")
//  stopSupervisor ! ReadFile

  class EagerFBPActor extends FileBasedPersistentActor {
    override def preStart(): Unit = {
      log.info("Eager Actor starting")
      dataSource = Source.fromFile(new File("src/main/resources/testFiles/important.txt"))
    }
  }

//  val eagerActor = system.actorOf(Props[EagerFBPActor])
  // ActorInitializationException => Stop is default supervision strategy

  val repeatedSupervisorProps = BackoffSupervisor.props(
    BackoffOpts
      .onStop(
        Props[EagerFBPActor],
        "eagerActor",
        1.seconds,
        30.seconds,
        0.1
      )
  )

  val repeatedSupervisor = system.actorOf(repeatedSupervisorProps, "eagerSupervisor")

  /*
    eagerSupervisor
      - child eagerActor
        - will die on start with ActorInitializationException
        - trigger the supervision strategy in eagerSupervisor => STOP eagerActor
      - backoff will kick in after 1 second, 2 s, 4, 8, 16
   */
}
