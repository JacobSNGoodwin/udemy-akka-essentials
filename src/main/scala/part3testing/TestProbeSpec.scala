package part3testing

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class TestProbeSpec extends TestKit(ActorSystem("TestProbeSpec"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll {

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  import TestProbeSpec._

  "A master actor" should {
    "register a slave" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)
    }

    "send the work to the slave actor" in {
      val master = system.actorOf(Props[Master])
      val slave = TestProbe("slave")

      master ! Register(slave.ref)
      expectMsg(RegistrationAck)

      val workloadString = "I love Akka"

      master ! Work(workloadString) // implicit sender is testActor

      // the interaction between the master and the slave actor
      slave.expectMsg(SlaveWork(workloadString, testActor))
      slave.reply(WorkCompleted(3, testActor)) // mocking reply to master

      expectMsg(Report(3)) // testActor receives this message
    }

    "aggregate data correctly" in {
        val master = system.actorOf(Props[Master])
        val slave = TestProbe("slave")

        master ! Register(slave.ref)
        expectMsg(RegistrationAck)

        val workloadString = "I love Akka"

        // this time count should be 6
        master ! Work(workloadString) // implicit sender is testActor
        master ! Work(workloadString)

        // in the meantime, I don't have a slave actor
        slave.receiveWhile() {
          // backticks mean exact value in match, not a variable to be used in match case
          case SlaveWork(`workloadString`, `testActor`) => slave.reply(WorkCompleted(3, testActor))
        }
        expectMsg(Report(3))
        expectMsg(Report(6))
      }
    }
}

object TestProbeSpec {
  // scenario
  /*
    word counting actor hierarchy master-slave

    send some work to the master
      - master sends the slave the piece of work
      - slave processes the work and replies to master
      - master aggregates the results
      -
    master sends the total count to the original requester
   */

  case class Work(text: String)
  case class WorkCompleted(count: Int, originalRequester: ActorRef)
  case class SlaveWork(text: String, originalRequester: ActorRef)
  case class Register(slaveRef: ActorRef)
  case object RegistrationAck
  case class Report(totalCount: Int)

  class Master extends Actor {
    override def receive: Receive = {
      case Register(slaveRef) =>
        sender() ! RegistrationAck
        context.become(online(slaveRef, 0))
      case _ =>
    }

    def online(slaveRef: ActorRef, totalWordCount: Int): Receive = {
      case Work(text) => slaveRef ! SlaveWork(text, sender())
      case WorkCompleted(count, originalRequester) =>
        val newTotalWordCount = totalWordCount + count
        originalRequester ! Report(newTotalWordCount)
        context.become(online(slaveRef, newTotalWordCount))
    }
  }

  // class Slave extends Actor ... defined by team, but we want to test Master actor
}
