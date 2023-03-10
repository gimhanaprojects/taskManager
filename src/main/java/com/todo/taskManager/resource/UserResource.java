package com.todo.taskManager.resource;

import com.todo.taskManager.constant.FileConstant;
import com.todo.taskManager.constant.SecurityConstant;
import com.todo.taskManager.constant.UserImplConstant;
import com.todo.taskManager.domain.HttpResponse;
import com.todo.taskManager.domain.User;
import com.todo.taskManager.domain.UserPrincipal;
import com.todo.taskManager.exception.domain.EmailExistException;
import com.todo.taskManager.exception.domain.EmailNotFoundException;
import com.todo.taskManager.exception.domain.ExceptionHandling;
import com.todo.taskManager.exception.domain.NotAnImageFileException;
import com.todo.taskManager.exception.domain.UsernameExistException;
import com.todo.taskManager.service.UserService;
import com.todo.taskManager.utility.JWTTokenProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import javax.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = { "", "/user" })
public class UserResource extends ExceptionHandling {

  private UserService userService;
  private AuthenticationManager authenticationManager;
  private JWTTokenProvider jwtTokenProvider;

  @Autowired
  public UserResource(
    UserService userService,
    AuthenticationManager authenticationManager,
    JWTTokenProvider jwtTokenProvider
  ) {
    this.userService = userService;
    this.authenticationManager = authenticationManager;
    this.jwtTokenProvider = jwtTokenProvider;
  }

  @PostMapping("/login")
  public ResponseEntity<User> login(@RequestBody User user) {
    authenticate(user.getUsername(), user.getPassword());
    User loginUser = userService.findUserByUsername((user.getUsername()));
    UserPrincipal userPrincipal = new UserPrincipal(loginUser);
    System.out.println("THAHAHT " + userPrincipal.getAuthorities());
    HttpHeaders jwtHeader = getJwtHeader(userPrincipal);
    return new ResponseEntity<>(loginUser, jwtHeader, HttpStatus.OK);
  }

  @PostMapping("/add")
  public ResponseEntity<User> addNewUser(
    @RequestParam("firstName") String firstName,
    @RequestParam("lastName") String lastName,
    @RequestParam("username") String username,
    @RequestParam("email") String email,
    @RequestParam("role") String role,
    @RequestParam("isActive") String isActive,
    @RequestParam("isNotLocked") String isNotLocked,
    @RequestParam(
      value = "profileImage",
      required = false
    ) MultipartFile profileImage
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    User newUser = userService.addNewUser(
      firstName,
      lastName,
      username,
      email,
      role,
      Boolean.parseBoolean(isNotLocked),
      Boolean.parseBoolean(isActive),
      profileImage
    );

    return new ResponseEntity<>(newUser, HttpStatus.OK);
  }

  @PostMapping("/update")
  public ResponseEntity<User> update(
    @RequestParam("currentUsername") String currentUsername,
    @RequestParam("firstName") String firstName,
    @RequestParam("lastName") String lastName,
    @RequestParam("username") String username,
    @RequestParam("email") String email,
    @RequestParam("role") String role,
    @RequestParam("isActive") String isActive,
    @RequestParam("isNotLocked") String isNotLocked,
    @RequestParam(
      value = "profileImage",
      required = false
    ) MultipartFile profileImage
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    User updatedUser = userService.updateUser(
      currentUsername,
      firstName,
      lastName,
      username,
      email,
      role,
      Boolean.parseBoolean(isNotLocked),
      Boolean.parseBoolean(isActive),
      profileImage
    );

    return new ResponseEntity<>(updatedUser, HttpStatus.OK);
  }

  @GetMapping("/find/{username}")
  public ResponseEntity<User> getUser(
    @PathVariable("username") String username
  ) {
    User user = userService.findUserByUsername(username);
    return new ResponseEntity<>(user, HttpStatus.OK);
  }

  @GetMapping("/list")
  public ResponseEntity<List<User>> getAllUsers() {
    List<User> users = userService.getUsers();
    return new ResponseEntity<>(users, HttpStatus.OK);
  }

  @GetMapping("/resetPassword/{email}")
  public ResponseEntity<HttpResponse> resetPassword(
    @PathVariable("email") String email
  ) throws EmailNotFoundException, MessagingException {
    userService.resetPassword(email);
    return response(HttpStatus.OK, UserImplConstant.EMAIL_SENT + email);
  }

  // @DeleteMapping("/delete/{id}")
  // @PreAuthorize("hasAnyAuthority('user:delete')")
  // public ResponseEntity<HttpResponse> deleteUser(@PathVariable("id") long id) {
  // userService.deleteUser(id);
  // return response(HttpStatus.OK, UserImplConstant.USER_DELETED_SUCESSFULLY);
  // }

  @DeleteMapping("/delete/{username}")
  @PreAuthorize("hasAnyAuthority('user:delete')")
  public ResponseEntity<HttpResponse> deleteUser(
    @PathVariable("username") String username
  ) throws IOException {
    userService.deleteUser(username);
    return response(HttpStatus.OK, UserImplConstant.USER_DELETED_SUCESSFULLY);
  }

  @PostMapping("/updateProfileImage")
  public ResponseEntity<User> updateProfileImage(
    @RequestParam("username") String username,
    @RequestParam(value = "profileImage") MultipartFile profileImage
  )
    throws UsernameNotFoundException, UsernameExistException, EmailExistException, IOException, NotAnImageFileException {
    User user = userService.updateProfileImage(username, profileImage);
    return new ResponseEntity<>(user, HttpStatus.OK);
  }

  @GetMapping(
    path = "/image/{username}/{fileName}",
    produces = MimeTypeUtils.IMAGE_JPEG_VALUE
  )
  public byte[] getProfileImage(
    @PathVariable("username") String username,
    @PathVariable("fileName") String fileName
  ) throws IOException {
    return Files.readAllBytes(
      Paths.get(
        FileConstant.USER_FOLDER +
        username +
        FileConstant.FORWARD_SLASH +
        fileName
      )
    );
  }

  @GetMapping(
    path = "/image/profile/{username}",
    produces = MimeTypeUtils.IMAGE_JPEG_VALUE
  )
  public byte[] getTempProfileImage(@PathVariable("username") String username)
    throws IOException {
    URL url = new URL(FileConstant.TEMP_PROFILE_IMAGE_BASE_URL + username);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    try (InputStream inputStream = url.openStream()) {
      int bytesRead;
      byte[] chunk = new byte[1024];
      while ((bytesRead = inputStream.read(chunk)) > 0) {
        byteArrayOutputStream.write(chunk, 0, bytesRead);
      }
    }
    return byteArrayOutputStream.toByteArray();
  }

  private ResponseEntity<HttpResponse> response(
    HttpStatus httpStatus,
    String message
  ) {
    HttpResponse body = new HttpResponse(
      httpStatus.value(),
      httpStatus,
      httpStatus.getReasonPhrase().toUpperCase(),
      message.toUpperCase()
    );
    return new ResponseEntity<>(body, httpStatus);
  }

  @PostMapping("/register")
  public ResponseEntity<User> register(@RequestBody User user)
    throws EmailExistException, UsernameNotFoundException, UsernameExistException, MessagingException {
    User newUser = userService.register(
      user.getFirstName(),
      user.getLastName(),
      user.getUsername(),
      user.getEmail()
    );
    return new ResponseEntity<>(newUser, HttpStatus.OK);
  }

  private HttpHeaders getJwtHeader(UserPrincipal userPrincipal) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(
      SecurityConstant.JWT_TOKEN_HEADER,
      jwtTokenProvider.generateJwtToken(userPrincipal)
    );
    return headers;
  }

  private void authenticate(String username, String password) {
    authenticationManager.authenticate(
      new UsernamePasswordAuthenticationToken(username, password)
    );
  }
}
