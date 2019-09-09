package part1recap

import scala.concurrent.Future

object ThreadModelLimitations extends App {
  /*
    - OOP encapsulation is only valid in the Single-threaded model
   */

  class BankAccount(private var amount: Int) {
    override def toString: String = "" + amount

    def withdraw(money: Int) = this.amount -= money
    def deposit(money: Int) = this.amount += money
    def getAmount = amount
  }

//  val account = new BankAccount(2000)
//  for(_ <- 10 to 1000) {
//    new Thread(() => account.withdraw(1)).start()
//  }
//
//  for(_ <- 10 to 1000) {
//    new Thread(() => account.deposit(1)).start()
//  }
//
//  println(account.getAmount) // DOES NOT YIELD ORIGINAL 2000 DOLLARS
  // OOP encapsulation is broken in a multithreaded env

  // synchronization! Locks to the rescue - wrap methods in this.synchronized

  // deadlocks, livelocks

  /*
    delegating something to a thread is a pain
    - how to send a signal to a thread that is already running
   */

  // you have a running thread and you want to pass a runnable to that thread
  var task: Runnable = null
  // consumer
  val runningThread: Thread = new Thread(() => {
    while (true) {
      while (task == null) {
        runningThread.synchronized {
          println("[background] waiting for a task...")
          runningThread.wait()
        }
      }
      task.synchronized {
        println("[background] I have a task")
        task.run()
        task = null
      }
    }
  })

  // producer
  def delegateToBackgroundThread(r: Runnable) = {
    if (task == null) task = r

    runningThread.synchronized {
      runningThread.notify()
    }
  }

  runningThread.start()

  Thread.sleep(3000)
  delegateToBackgroundThread(() => println(42))
  Thread.sleep(3000)
  delegateToBackgroundThread(() => println("this should run in the background"))

  /*
    Need data structure that
      - can safely receive messages
      - can identify the sender
      - is easily identifiable
      - can guard against errors
   */

  /*
    tracing and dealing with errors in a multithreaded env is a pain
   */

  // 1M numbers in between 10 threads
  import scala.concurrent.ExecutionContext.Implicits.global

  val futures = (1 to 9)
    .map(i => 100000 * i until 100000 * (i + 1)) // 0-99999, 100000-199999, 200000 - 299999, ...
    .map(range => Future {
      if (range.contains(555555)) throw new RuntimeException("invalid number")
      range.sum
    })

  val sumFuture = Future.reduceLeft(futures)(_ + _) // Future with the sum of all the numbers
  sumFuture.onComplete(println) // Failure(java.lang.RuntimeException: invalid number) ... how to debug???
}
