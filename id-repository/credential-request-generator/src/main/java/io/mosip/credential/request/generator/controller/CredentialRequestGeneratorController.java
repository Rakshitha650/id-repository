package io.mosip.credential.request.generator.controller;

import io.mosip.credential.request.generator.init.CredentialInstializer;
import io.mosip.credential.request.generator.init.SubscribeEvent;
import io.mosip.idrepository.core.logger.IdRepoLogger;
import io.mosip.idrepository.core.security.IdRepoSecurityManager;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.JsonUtils;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.mosip.credential.request.generator.dto.CredentialStatusEvent;
import io.mosip.credential.request.generator.exception.CredentialrRequestGeneratorException;
import io.mosip.credential.request.generator.service.CredentialRequestService;
import io.mosip.idrepository.core.dto.CredentialIssueRequestDto;
import io.mosip.idrepository.core.dto.CredentialIssueResponse;
import io.mosip.idrepository.core.dto.CredentialIssueResponseDto;
import io.mosip.idrepository.core.dto.CredentialIssueStatusResponse;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.websub.api.annotation.PreAuthenticateContentAndVerifyIntent;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import java.util.HashMap;
import java.util.Map;


/**
 * The Class CredentialRequestGeneratorController.
 *
 * @author Sowmya
 */
@RestController
@Api(tags = "Credential Request Renerator")
public class CredentialRequestGeneratorController {

	private static final Logger LOGGER = IdRepoLogger.getLogger(CredentialRequestGeneratorController.class);

	/** The credential request service. */
	@Autowired
	private CredentialRequestService credentialRequestService;

	@Autowired
	private CredentialInstializer credentialInstializer;

	@Autowired
	private SubscribeEvent subscribeEvent;

	@Autowired
	JobLauncher jobLauncher;

	@Autowired
	Job job;



	/**
	 * Credential issue.
	 *
	 * @param credentialIssueRequestDto the credential issue request dto
	 * @return the response entity
	 */
	@PreAuthorize("hasAnyRole('CREDENTIAL_REQUEST')")
	@PostMapping(path = "/requestgenerator", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "Create the  credential issuance request", response = CredentialIssueResponseDto.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Created request id successfully"),
			@ApiResponse(code = 400, message = "Unable to get request id") })
	public ResponseEntity<Object> credentialIssue(
			@RequestBody  RequestWrapper<CredentialIssueRequestDto>  credentialIssueRequestDto) {

		ResponseWrapper<CredentialIssueResponse> credentialIssueResponseWrapper = credentialRequestService
				.createCredentialIssuance(credentialIssueRequestDto.getRequest());
		return ResponseEntity.status(HttpStatus.OK).body(credentialIssueResponseWrapper);
	}
	@PreAuthorize("hasAnyRole('CREDENTIAL_REQUEST')")
	@GetMapping(path = "/cancel/{requestId}", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "cancel the credential issuance request", response = CredentialIssueResponseDto.class)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "cancel the request successfully"),
	@ApiResponse(code=400,message="Unable to cancel the request"),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ResponseBody
	public ResponseEntity<Object> cancelCredentialRequest(@PathVariable("requestId") String requestId) {

		ResponseWrapper<CredentialIssueResponse> credentialIssueResponseWrapper = credentialRequestService
				.cancelCredentialRequest(requestId);
		return ResponseEntity.status(HttpStatus.OK).body(credentialIssueResponseWrapper);
	}

	@PreAuthorize("hasAnyRole('CREDENTIAL_REQUEST')")
	@GetMapping(path = "/get/{requestId}", consumes = MediaType.ALL_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiOperation(value = "get credential issuance request status", response = CredentialIssueResponseDto.class)
	@ApiResponses(value = {
			@ApiResponse(code = 200, message = "get the credential issuance status of request successfully"),
			@ApiResponse(code = 400, message = "Unable to get the status of credential issuance request"),
			@ApiResponse(code = 500, message = "Internal Server Error") })
	@ResponseBody
	public ResponseEntity<Object> getCredentialRequestStatus(@PathVariable("requestId") String requestId) {

		ResponseWrapper<CredentialIssueStatusResponse> credentialIssueResponseWrapper = credentialRequestService
				.getCredentialRequestStatus(requestId);
		return ResponseEntity.status(HttpStatus.OK).body(credentialIssueResponseWrapper);
	}



	@PostMapping(path = "/callback/notifyStatus", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Request authenticated successfully") })
	@PreAuthenticateContentAndVerifyIntent(secret = "test", callback = "/v1/credentialrequest/callback/notifyStatus", topic = "CREDENTIAL_STATUS_UPDATE")
	public ResponseWrapper<?> handleSubscribeEvent( @RequestBody CredentialStatusEvent credentialStatusEvent) throws CredentialrRequestGeneratorException {
		credentialRequestService.updateCredentialStatus(credentialStatusEvent);
		return new ResponseWrapper<>();
	}

	@GetMapping(path = "/scheduleRetrySubscription")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Request authenticated successfully") })
	public String handleReSubscribeEvent() {
		return credentialInstializer.scheduleRetrySubscriptions();
	}

	@GetMapping(path = "/scheduleWebsubSubscription")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Request authenticated successfully") })
	public String handleSubscribeEvent() {
		return subscribeEvent.scheduleSubscription();
	}

	@GetMapping(path = "/startCredentialBatch")
	@ApiResponses(value = { @ApiResponse(code = 200, message = "Request authenticated successfully") })
	public String handleCredentialBatchEvent() {
		try {
			Map<String, JobParameter> parameters = new HashMap<>();
			JobExecution jobExecution = jobLauncher.run(job, new JobParameters(parameters));
			return JsonUtils.javaObjectToJsonString(jobExecution);
		} catch (Exception e) {
			LOGGER.error(IdRepoSecurityManager.getUser(), "/startCredentialBatch",
					"Error calling startCredentialBatch API", ExceptionUtils.getStackTrace(e));
			return "Error occured. Check logs for more details";
		}
	}

}