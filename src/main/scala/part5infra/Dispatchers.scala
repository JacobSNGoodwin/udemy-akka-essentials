package part5infra

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

object Dispatchers extends App {


  class Counter extends Actor with ActorLogging {
    var count = 0

    override def receive: Receive = {
      case message =>
        count += 1
        log.info(s"[$count] $message")
    }
  }

  val system = ActorSystem("DispatchersDemo")

  // Method 1 - Programmatic, or in code
  val actors = for (i <- 1 to 10)
    yield system.actorOf(Props[Counter].withDispatcher("my-dispatcher"), s"counter_$i")

//  val r = new Random()
//  for (i <- 1 to 1000) {
//    actors(r.nextInt(10)) ! i
//  }

  // Method 2 - From config
//  val rtjvmActor = system.actorOf(Props[Counter], "rtjvm")

  /*
    Dispatchers implements the ExecutionContext trait
   */

  // generally discourage running with context.dispatcher, will block waiting for future
  class DBActor extends Actor with ActorLogging {
//    implicit val executionContext: ExecutionContext = context.dispatcher

    // solution #1
    implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup("my-dispatcher")
    // solution #2 - use router

    override def receive: Receive = {
      case message => Future {
        // wait on a resource
        Thread.sleep(5000)
        log.info(s"Success: $message")
      }
    }
  }

  val dbActor = system.actorOf(Props[DBActor])
//  dbActor ! "the meaning of life is 42"

  val nonblockingActor = system.actorOf(Props[Counter])
  for (i <- 1 to 1000) {
    val message = s"important message $i"
    dbActor ! message
    nonblockingActor ! message
  }
}
