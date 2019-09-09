package part2actors

import java.text.ParsePosition

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import part2actors.ActorCapabilities.BankAccount.Deposit
import part2actors.ActorCapabilities.Person.LiveTheLife


object ActorCapabilities extends App {
  class SimpleActor extends Actor {
    override def receive: Receive = {
      case "Hi!" => sender() ! "Hello there!" // replying to a message
      case message: String => println(s"[${context.self}] I have received: $message")
      case number: Int => println(s"[simple actor] I have received a number: $number")
      case SpecialMessage(content) => println(s"[simple actor] I have received a special message: $content")
      case SendMessageToYourself(content) =>
        self ! content // send to self (a string)
      case SayHiTo(ref) => ref ! "Hi!" // implicitly uses self reference
      case WirelessPhoneMessage(content, ref) => ref forward (content + "s") // keep the original sender
    }
  }

  val system = ActorSystem("actorCapabilitiesDemo")
  val simpleActor = system.actorOf(Props[SimpleActor], "simpleActor")

  simpleActor ! "hello, actor"

  // 1 - messages can be of any type
  // a) messages must be IMMUTABLE
  // b) messages must be SERIALIZABLE - can be sent via byte stream
  // in practice, use case classes and case object

  simpleActor ! 42

  case class SpecialMessage(content: String)

  simpleActor ! SpecialMessage("some special content")

  // 2 - actors have information about their context and about themselves
  // context.self equivalent to 'this' (can use self)

  case class SendMessageToYourself(content: String)
  simpleActor ! SendMessageToYourself("I am an actor and I am proud of it!")

  // 3 - actors can REPLY to messages
  val alice = system.actorOf(Props[SimpleActor], "alice")
  val bob = system.actorOf(Props[SimpleActor], "bob")

  case class SayHiTo(ref: ActorRef)

  alice ! SayHiTo(bob) // noSender (bob gets noSender)

  // 4 - dead letters
  alice ! "Hi!" // reply to "me" - Get Info message about sending to dead letters (there's no sender to reply to)

  // 5 - forwarding messages = sneding a message with the ORIGINAL sender

  case class WirelessPhoneMessage(content: String, ref: ActorRef)
  alice ! WirelessPhoneMessage("Hi", bob)

  /*
    1. a Counter actor
      - Increment
      - Decrement
      - Print internal counter

    2. a Bank account as an actor
      receive
        - Deposit an amount
        - Withdraw an amount
        - Statement
      replies with
        - success
        - failure

      interact with some other kind of actor to send statements to bank acount
   */

  // Domain of the counter
  object Counter {
    case object Increment
    case object Decrement
    case object Print
  }
  class Counter extends Actor {
    import Counter._
    var count = 0

    override def receive: Receive = {
      case Increment => count += 1
      case Decrement => count -= 1
      case Print => println(s"[${self.path}] - Current count: $count")
    }
  }

  val counter = system.actorOf(Props[Counter], "myCounter")
  import Counter._

  counter ! Print
  counter ! Increment
  counter ! Increment
  counter ! Increment
  counter ! Print
  counter ! Decrement
  counter ! Print

  // bank account
  object BankAccount {
    case class Deposit(amount: Int)
    case class Withdraw(amount: Int)
    case object Statement

    case class TransactionSuccess(message: String)
    case class TransactionFailure(reason: String)
  }
  class BankAccount extends Actor {
    import BankAccount._
    var funds = 0

    override def receive: Receive = {
      case Deposit(amount) =>
        if (amount < 0) sender() ! TransactionFailure("invalid deposit amount")
        else {
          funds += amount
          sender() ! TransactionSuccess(s"successfully deposited $amount")
        }
      case Withdraw(amount) =>
        if (amount < 0) sender() ! TransactionFailure("invalid withdrawal amount")
        else if (amount > funds) sender() ! TransactionFailure("insufficient funds")
        else {
          funds -= amount
          sender() ! TransactionSuccess(s"successfully withdrew $amount")
        }
      case Statement => sender() ! s"Your balance is $funds"
    }
  }

  // person interacts with BankAccount
  object Person {
    case class LiveTheLife(account: ActorRef)
  }
  class Person extends Actor {
    import Person._
    import BankAccount._

    override def receive: Receive = {
      case LiveTheLife(account) =>
        account ! Deposit(7000)
        account ! Withdraw(90000)
        account ! Withdraw(500)
        account ! Statement
      case message => println(message.toString)
    }
  }

  val account = system.actorOf(Props[BankAccount], "bankAccount")
  val person = system.actorOf(Props[Person], "aPerson")

  person ! LiveTheLife(account)
}
