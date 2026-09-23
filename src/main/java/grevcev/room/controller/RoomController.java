package grevcev.room.controller;

import grevcev.room.dto.CreateRoomRequest;
import grevcev.reservation.dto.PageResponse;
import grevcev.room.dto.RoomResponse;
import grevcev.room.dto.UpdateRoomRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import grevcev.room.service.RoomService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/rooms")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Комнаты", description = "CRUD операций над комнатами отеля")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(
            summary = "Получить комнату по ID",
            description = "Возвращает данные комнаты без списка её бронирований."
    )
    @ApiResponse(responseCode = "200", description = "Комната найдена")
    @ApiResponse(responseCode = "404", description = "Комната не найдена")
    @GetMapping("/{id}")
    public RoomResponse getRoom(@PathVariable Long id){
        return roomService.findById(id);
    }

    @Operation(
            summary = "Список комнат с пагинацией и сортировкой",
            description = "Возвращает страницу комнат. По умолчанию сортировка по названию."
    )
    @ApiResponse(responseCode = "200", description = "Страница комнат")
    @GetMapping
    public PageResponse<RoomResponse> getRooms(@PageableDefault(size = 20, sort = "name") Pageable pageable){
        return PageResponse.from(roomService.findAll(pageable));
    }

    @Operation(
            summary = "Создать комнату",
            description = "Создаёт новую комнату с указанным названием и вместимостью."
    )
    @ApiResponse(responseCode = "201", description = "Комната создана")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации (пустое название, отрицательная вместимость)")
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest request){
        RoomResponse created = roomService.save(request);
        return ResponseEntity
                .created(buildLocation(created.id()))
                .body(created);
    }

    private URI buildLocation(Long id) {
        return ServletUriComponentsBuilder.fromCurrentRequest()   // /api/v1/users
                .path("/{id}")                                     // + /5
                .buildAndExpand(id)
                .toUri();
    }

    @Operation(
            summary = "Обновить комнату",
            description = "Обновляет название и вместимость комнаты."
    )
    @ApiResponse(responseCode = "200", description = "Комната обновлена")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации")
    @ApiResponse(responseCode = "404", description = "Комната не найдена")
    @PutMapping("/{id}")
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody UpdateRoomRequest request){
        return roomService.update(id, request);
    }

    @Operation(
            summary = "Удалить комнату",
            description = """
            Удаляет комнату. При наличии связанных бронирований операция запрещена
            FK-ограничением — возвращается 409 для защиты целостности данных.
            """
    )
    @ApiResponse(responseCode = "204", description = "Комната удалена")
    @ApiResponse(responseCode = "404", description = "Комната не найдена")
    @ApiResponse(responseCode = "409", description = "У комнаты есть связанные бронирования")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id){
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
