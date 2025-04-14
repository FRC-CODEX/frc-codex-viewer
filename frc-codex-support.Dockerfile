FROM public.ecr.aws/lambda/python:3.13

# Copy requirements.txt
COPY requirements-support.txt ${LAMBDA_TASK_ROOT}

# Install the specified packages
RUN pip install -r requirements-support.txt

# Copy function code
COPY support ${LAMBDA_TASK_ROOT}/support
RUN mv ${LAMBDA_TASK_ROOT}/support/lambda_function.py ${LAMBDA_TASK_ROOT}
USER nobody

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "lambda_function.lambda_handler" ]
