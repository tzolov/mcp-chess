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

import com.github.alexandreroman.chessimage.ChessRenderer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.annotation.RegisterReflection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ReflectionUtils;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Configuration(proxyBeanMethods = false)
@RegisterReflection(memberCategories = MemberCategory.INVOKE_DECLARED_METHODS)
class McpConfig {
    private final Logger logger = LoggerFactory.getLogger(McpConfig.class);

    @Bean
    ToolCallbackProvider chessToolsProvider(ChessTools chessTools) {
        // Register Spring AI tools as MCP tools.
        return MethodToolCallbackProvider.builder().toolObjects(chessTools).build();
    }

    @Bean
    public List<McpServerFeatures.SyncPromptRegistration> prompts() {
        return List.of(
                new McpServerFeatures.SyncPromptRegistration(new McpSchema.Prompt(
                        "start-a-new-game",
                        "A prompt to start playing a chess game",
                        Collections.emptyList()), req -> {
                    final var m = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent("Let's play a chess game. Check that each move is legal. Suggest the best move to play."));
                    return new McpSchema.GetPromptResult("A message to start playing a chess game", List.of(m));
                })
        );
    }

    @Bean
    List<McpServerFeatures.SyncToolRegistration> syncToolsReg() {
        // We're about to declare our own tool for generating board images.
        // We need to generate a JSON schema for the tool signature:
        // let's reuse utility classes from Spring AI with a dummy tool method.
        final var generateBoardImageMethod = ReflectionUtils.findMethod(McpConfig.class, "dummyGenerateBoardImage", String.class);
        assert generateBoardImageMethod != null;
        final var generateBoardImageInputSchema = JsonSchemaGenerator.generateForMethodInput(generateBoardImageMethod);

        // This is the MCP tool definition we need, including a JSON schema for the signature and a description.
        final var tool = new McpSchema.Tool("chess_generate_board_image",
                "Generate a board image in a chess game from a Forsyth-Edwards Notation (FEN).",
                generateBoardImageInputSchema);

        return List.of(
                new McpServerFeatures.SyncToolRegistration(tool, req -> {
                    try {
                        // Get FEN from the tool arguments.
                        final var fen = (String) req.get("fen");

                        logger.atInfo().log("Rendering board to PNG image: {}", fen);
                        final var out = new ByteArrayOutputStream(1024 * 4);
                        new ChessRenderer().render(fen, out);

                        logger.atInfo().log("Encoding PNG board image to base64: {}", fen);
                        final var imgB64 = Base64.getEncoder().encodeToString(out.toByteArray());

                        // We take care of returning an image using the MCP schema:
                        // Spring AI currently doesn't support this feature.
                        final List<McpSchema.Content> contents = List.of(
                                new McpSchema.ImageContent(Collections.singletonList(McpSchema.Role.USER),
                                        1.0d, "image", imgB64, "image/png")
                        );
                        return new McpSchema.CallToolResult(contents, false);
                    } catch (Exception e) {
                        final var msg = "Failed to generate board image: %s".formatted(e.getMessage());
                        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(msg)), true);
                    }
                })
        );
    }

    private void dummyGenerateBoardImage(@ToolParam(description = "Board state in Forsyth-Edwards Notation") String fen) {
        // This method does nothing on purpose.
        // We just want to generate a JSON schema from its arguments.
    }
}
