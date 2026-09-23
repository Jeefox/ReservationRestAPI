package grevcev.user.controller;

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
import grevcev.user.dto.CreateUserRequest;
import grevcev.reservation.dto.PageResponse;
import grevcev.user.dto.UpdateUserRequest;
import grevcev.user.dto.UserResponse;
import grevcev.user.service.UserService;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/users")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Пользователи", description = "CRUD операций над пользователями системы")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Получить пользователя по ID",
            description = "Возвращает данные пользователя без его бронирований."
    )
    @ApiResponse(responseCode = "200", description = "Пользователь найден")
    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    @GetMapping("/{id}")
    public UserResponse getUser(@PathVariable Long id){
        return userService.findById(id);
    }

    @Operation(
            summary = "Список пользователей с пагинацией и сортировкой",
            description = "Возвращает страницу пользователей. По умолчанию сортировка по имени."
    )
    @ApiResponse(responseCode = "200", description = "Страница пользователей")
    @GetMapping
    public PageResponse<UserResponse> getUsers(
            @PageableDefault(size = 20, sort = "name") Pageable pageable){
        return PageResponse.from(userService.findAll(pageable));
    }

    @Operation(
            summary = "Создать пользователя",
            description = """
            Создаёт нового пользователя. Email должен быть уникальным — при дубликате
            возвращается 409 (защита уникального индекса БД).
            """
    )
    @ApiResponse(responseCode = "201", description = "Пользователь создан")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации (некорректный email, пустое имя)")
    @ApiResponse(responseCode = "409", description = "Пользователь с таким email уже существует")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request){
        UserResponse created = userService.save(request);
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
            summary = "Обновить пользователя",
            description = "Обновляет имя и email. Проверка уникальности email выполняется на уровне БД."
    )
    @ApiResponse(responseCode = "200", description = "Пользователь обновлён")
    @ApiResponse(responseCode = "400", description = "Ошибка валидации")
    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    @ApiResponse(responseCode = "409", description = "Новый email уже используется другим пользователем")
    @PutMapping("/{id}")
    public UserResponse updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request){
        return userService.update(id, request);
    }

    @Operation(
            summary = "Удалить пользователя",
            description = """
            Удаляет пользователя. При наличии связанных бронирований операция запрещена
            FK-ограничением — возвращается 409 для защиты целостности данных.
            """
    )
    @ApiResponse(responseCode = "204", description = "Пользователь удалён")
    @ApiResponse(responseCode = "404", description = "Пользователь не найден")
    @ApiResponse(responseCode = "409", description = "У пользователя есть связанные бронирования")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id){
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
