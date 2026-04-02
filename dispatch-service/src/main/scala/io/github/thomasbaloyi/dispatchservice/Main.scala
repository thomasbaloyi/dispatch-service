package io.github.thomasbaloyi.dispatchservice

import cats.effect.{IO, IOApp}

object Main extends IOApp.Simple:
  val run = DispatchserviceServer.run[IO]
