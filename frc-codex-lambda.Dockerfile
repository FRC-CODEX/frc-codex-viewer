FROM public.ecr.aws/lambda/python:3.13

RUN mkdir /tmp/_HTTP_CACHE && chown nobody:nobody /tmp/_HTTP_CACHE
COPY --chown=nobody _HTTP_CACHE /tmp/_HTTP_CACHE

# Copy requirements.txt
COPY requirements.txt ${LAMBDA_TASK_ROOT}

# Install the specified packages
RUN pip install -r requirements.txt

# Copy function code
COPY processor ${LAMBDA_TASK_ROOT}/processor
COPY lambda_function.py ${LAMBDA_TASK_ROOT}

USER nobody

# Set the CMD to your handler (could also be done as a parameter override outside of the Dockerfile)
CMD [ "lambda_function.lambda_handler" ]
