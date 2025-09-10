package ao.co.isptec.aplm.cidadaoactivo.api.controllers

import ao.co.isptec.aplm.cidadaoactivo.api.dtos.LoginRequest
import ao.co.isptec.aplm.cidadaoactivo.api.dtos.LoginResponse
import ao.co.isptec.aplm.cidadaoactivo.api.dtos.LogoutRequest
import ao.co.isptec.aplm.cidadaoactivo.api.dtos.SignupRequest
import ao.co.isptec.aplm.cidadaoactivo.api.models.User
import ao.co.isptec.aplm.cidadaoactivo.api.services.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
@CrossOrigin
class AuthController(private val userService: UserService) {
    @PostMapping("/login")
    fun login(@RequestBody requestParam: LoginRequest): ResponseEntity<LoginResponse?> {
        val user = userService.login(requestParam.username, requestParam.password) ?: return ResponseEntity(HttpStatus.BAD_REQUEST)
        return ResponseEntity(LoginResponse(user.id!!, user.username, user.name, user.sessionToken!!), HttpStatus.OK)
    }
    
    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest): ResponseEntity<Void> {
        return if (userService.logout(request.username, request.token)) {
            ResponseEntity(HttpStatus.OK)
        } else {
            ResponseEntity(HttpStatus.BAD_REQUEST)
        }
    }
    
    @PostMapping("/signup")
    fun signup(@RequestBody request: SignupRequest): ResponseEntity<Void> {
        val created = userService.createUser(User(
            id = null,
            name = request.name,
            username =  request.username,
            password = request.password
        ))
        return if (created != null) ResponseEntity(HttpStatus.OK)
        else ResponseEntity<Void>(HttpStatus.BAD_REQUEST)
    }
    
    @GetMapping("/users")
    fun getUsers(): ResponseEntity<List<User>> {
        return ResponseEntity(userService.getAllUsers(), HttpStatus.OK)
    }
}