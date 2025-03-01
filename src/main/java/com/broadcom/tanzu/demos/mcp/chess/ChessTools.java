/*
 * Copyright (c) 2025 Broadcom, Inc. or its affiliates
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.broadcom.tanzu.demos.mcp.chess;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import com.github.alexandreroman.chessimage.ChessRenderer;
import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.notation.NotationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
class ChessTools {
    private final Logger logger = LoggerFactory.getLogger(ChessTools.class);
    private final ChessEngine chessEngine;

    ChessTools(ChessEngine chessEngine) {
        this.chessEngine = chessEngine;
    }

    @Tool(name = "chess_guess_next_move", description = """
            Guess the next move to play in a chess game.
            This tool returns a move in UCI format (for instance: d2d3).
            If the next move is unknown, 'null' is returned.
            """)
    String guessNextMove(@ToolParam(description = "Board state in Forsyth-Edwards Notation") String fen) {
        logger.atDebug().log("Guessing next move from FEN: {}", fen);
        final var game = new ChessGame(fen);
        final var resp = chessEngine.getNextMove(game)
                .map(move -> game.getNotation(NotationType.UCI, move))
                .orElse(null);
        logger.atInfo().log("Guessed next move from FEN: {}=>{}", fen, resp);
        return resp;
    }

    @Tool(name = "chess_is_legal_move", description = """
            Check if a move is legal.
            The move is defined in UCI format (for instance: d2d3).
            """)
    boolean isLegalMove(@ToolParam(description = "Board state in Forsyth-Edwards Notation") String fen,
            @ToolParam(description = "Move in UCI format") String move) {
        logger.atDebug().log("Checking if the move {} is legal in FEN: {}", move, fen);
        final var game = new ChessGame(fen);
        final var resp = game.isLegalMove(game.getMove(NotationType.UCI, move));
        logger.atInfo().log("Is move {} legal in FEN {}? {}", move, fen, resp ? "yes" : "no");
        return resp;
    }

    @Tool(name = "chess_generate_board_image", description = """
            Generate a board image in a chess game from a Forsyth-Edwards Notation (FEN).
            Returns a base64-encoded PNG image.
            """)
    public String chessGenerateBoardImage(
            @ToolParam(description = "Board state in Forsyth-Edwards Notation") String fen) {
        logger.atInfo().log("Rendering board to PNG image: {}", fen);
        final var out = new ByteArrayOutputStream(1024 * 4);
        try {
            new ChessRenderer().render(fen, out);
            logger.atInfo().log("Encoding PNG board image to base64: {}", fen);
            final var imgB64 = Base64.getEncoder().encodeToString(out.toByteArray());
            return imgB64;
        } catch (IOException e) {
            return "error";
        }
    }
}
