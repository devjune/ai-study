package com.example.phase3

import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder

/** JLine 입력 루프. 빈 줄은 건너뛰고 exit, Ctrl-C, Ctrl-D로 끝낸다. ↑로 이전 입력을 다시 쓸 수 있다. */
class Repl(private val prompt: String = "❯ ") {

    suspend fun run(handle: suspend (String) -> String) {
        val terminal = TerminalBuilder.builder().system(true).build()
        val reader = LineReaderBuilder.builder().terminal(terminal).build()
        try {
            while (true) {
                val line = try {
                    reader.readLine(prompt)
                } catch (e: UserInterruptException) {
                    break
                } catch (e: EndOfFileException) {
                    break
                }
                if (line.isBlank()) continue
                if (line == "exit") break
                println(handle(line))
            }
        } finally {
            terminal.close()
        }
    }
}
