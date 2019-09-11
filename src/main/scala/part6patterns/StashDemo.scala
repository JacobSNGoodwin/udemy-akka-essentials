package part6patterns

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, Stash}

object StashDemo extends App {

  /*
    ResourceActor
      - open => it can receive read/write requests to the resource
      - otherwise it will postpone all read/write requests until the state is open

    ResourceActor is closed
      - Open => switch to the open state
      - Read, Write messages are POSTPONED

    ResourceActor is open
      - Read, Write are handled
      - Close => switch to the closed state

      [Open, Read, Read, Write]
      - switch to open state
      - read the data
      - read the data again
      - write the data

      [Read, Open, Write]
      - stash Read
        Stash: [Read]
      - Open => switch to the open state
        - Mailbox: [Read, Write]
      - read and write are handled

   */

  case object Open
  case object Close
  case object Read
  case class Write(data: String)

  // mix-in the Stash trait
  class ResourceActor extends Actor with ActorLogging with Stash {
    private var innerData: String = ""

    override def receive: Receive = closed

    def closed: Receive = {
      case Open =>
        log.info("Opening resource")
        unstashAll() // before changing context
        context.become(open)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the closed state")
        stash()
    }

    def open: Receive = {
      case Read =>
        log.info(s"I have read $innerData")
      case Write(data) =>
        log.info(s"I am writing $data")
        innerData = data
      case Close =>
        log.info("Closing resource")
        unstashAll()
        context.become(closed)
      case message =>
        log.info(s"Stashing $message because I can't handle it in the open state")
        stash()
    }
  }

  val system = ActorSystem("StashDemo")
  val resourceActor = system.actorOf(Props[ResourceActor])

  resourceActor ! Read // stashed
  resourceActor ! Open // switch to open -> pop previous Read -> ""
  resourceActor ! Open // stashed
  resourceActor ! Write("I love stash") // still in the open state
  resourceActor ! Close // switch to closed state and pop second Open from above -> switch back to open
  resourceActor ! Read // read "I love stash"
}
